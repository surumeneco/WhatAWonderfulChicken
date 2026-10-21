package co.surumene.whatawonderfulchicken.command;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bounded migration: at most 16 entity visits and 4 immutable files per tick.
 * Neither unloaded chunks nor offline inventories are force-loaded.
 * A file-level sweep protects their Genome references in the persistent world.
 */
public final class WorldStorageMigration<E extends Entity> implements AutoCloseable {
    private static final int ENTITY_VISITS_PER_TICK = 16;
    private static final int FILE_VISITS_PER_TICK = 4;
    private final JavaPlugin plugin;
    private final Class<E> entityType;
    private final Predicate<E> eligible;
    private final BiFunction<E, Boolean, Boolean> entityMigration;
    private final Supplier<List<Path>> directories;
    private final BiFunction<FileRecord, Boolean, Boolean> mirror;
    private Job job;
    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("wonderful-record-migration-", 0).factory());
    private boolean closed;

    public record FileRecord(Path directory, UUID id) {}

    public WorldStorageMigration(JavaPlugin plugin, Class<E> entityType,
            Predicate<E> eligible, BiFunction<E, Boolean, Boolean> entityMigration,
            Supplier<List<Path>> directories,
            BiFunction<FileRecord, Boolean, Boolean> mirror) {
        this.plugin = Objects.requireNonNull(plugin);
        this.entityType = Objects.requireNonNull(entityType);
        this.eligible = Objects.requireNonNull(eligible);
        this.entityMigration = Objects.requireNonNull(entityMigration);
        this.directories = Objects.requireNonNull(directories);
        this.mirror = Objects.requireNonNull(mirror);
    }

    public int start(CommandSender sender, boolean dryRun) {
        if (closed) throw new IllegalStateException("Migration worker is closed");
        if (job != null && job.status.equals("RUNNING")) {
            sender.sendMessage("WCL-MIGRATION job=busy status=FAILED processed=0 copied=0 skipped=0 failed=1");
            return 0;
        }
        Job next = new Job(UUID.randomUUID().toString(),
                List.copyOf(Bukkit.getWorlds()), List.copyOf(directories.get()), dryRun);
        job = next;
        sender.sendMessage(next.message());
        next.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> advance(next), 1L, 1L);
        return 1;
    }

    public int status(CommandSender sender) {
        if (job == null) {
            sender.sendMessage("WCL-MIGRATION job=none status=FAILED processed=0 copied=0 skipped=0 failed=1");
            return 0;
        }
        sender.sendMessage(job.message());
        return job.status.equals("SUCCESS") ? 1 : 0;
    }

    private void advance(Job active) {
        if (active != job || !active.status.equals("RUNNING")) return;
        try {
            if (!active.entitiesDone) visitEntities(active);
            else visitRecords(active);
            if (active.entitiesDone && active.recordsDone) complete(active);
        } catch (RuntimeException | IOException error) {
            active.failed++;
            plugin.getLogger().severe("World record migration aborted: " + error.getMessage());
            complete(active);
        }
    }

    private void visitEntities(Job active) {
        int attempts = 0;
        while (attempts < ENTITY_VISITS_PER_TICK) {
            if (active.entities == null || !active.entities.hasNext()) {
                if (active.chunks == null || active.chunkPosition >= active.chunks.length) {
                    if (active.worldPosition >= active.worlds.size()) {
                        active.entitiesDone = true;
                        return;
                    }
                    active.chunks = active.worlds.get(active.worldPosition++).getLoadedChunks();
                    active.chunkPosition = 0;
                    attempts++; // obtaining the world's loaded-chunk snapshot consumes the budget
                    continue;
                }
                if (active.chunkPosition >= active.chunks.length) continue;
                Chunk chunk = active.chunks[active.chunkPosition++];
                attempts++; // empty chunks also consume the per-tick budget
                if (!chunk.isLoaded()) {
                    active.failed++;
                    continue;
                }
                active.entities = Arrays.asList(chunk.getEntities()).iterator();
                continue;
            }
            attempts++;
            Entity entity = active.entities.next();
            if (!entityType.isInstance(entity)) continue;
            E typed = entityType.cast(entity);
            if (!eligible.test(typed)) continue;
            active.processed++;
            try {
                if (!typed.isValid()) throw new IllegalStateException("Entity unloaded during migration");
                if (entityMigration.apply(typed, active.dryRun)) active.copied++;
                else active.skipped++;
            } catch (RuntimeException error) {
                active.failed++;
                plugin.getLogger().warning("Entity record migration failed "
                        + typed.getUniqueId() + ": " + error.getMessage());
            }
        }
    }

    private void visitRecords(Job active) throws IOException {
        // CRC verification, disk reads and atomic file publication stay off the main thread.
        if (active.pendingCopy != null) {
            if (!active.pendingCopy.isDone()) return;
            try {
                if (active.pendingCopy.join()) active.copied++;
                else active.skipped++;
            } catch (RuntimeException error) {
                active.failed++;
                plugin.getLogger().warning("Record mirror failed "
                        + active.pendingPath + ": " + error.getMessage());
            } finally {
                active.pendingCopy = null;
                active.pendingPath = null;
            }
        }
        int visited = 0;
        while (visited < FILE_VISITS_PER_TICK) {
            if (active.files == null || !active.files.hasNext()) {
                active.closeDirectory();
                if (active.directoryPosition >= active.sourceDirectories.size()) {
                    active.recordsDone = true;
                    return;
                }
                Path directory = active.sourceDirectories.get(active.directoryPosition++);
                if (!Files.isDirectory(directory)) continue;
                active.openDirectory(directory);
                continue;
            }
            Path source = active.files.next();
            visited++;
            if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) continue;
            String filename = source.getFileName().toString();
            if (!filename.endsWith(".bin")) continue;
            UUID id;
            try { id = UUID.fromString(filename.substring(0, filename.length() - 4)); }
            catch (IllegalArgumentException ignored) { continue; }
            active.processed++;
            try {
                if (mirror.apply(new FileRecord(active.currentDirectory, id), active.dryRun)) active.copied++;
                else active.skipped++;
            } catch (RuntimeException error) {
                active.failed++;
                plugin.getLogger().warning("Record mirror failed " + source + ": " + error.getMessage());
            }
        }
    }

    private void complete(Job active) {
        active.closeDirectory();
        active.status = active.failed == 0 ? "SUCCESS" : "FAILED";
        active.task.cancel();
        active.task = null;
        plugin.getLogger().info("WCL migration finished: " + active.message());
    }

    private static final class Job {
        final String id;
        final List<World> worlds;
        final List<Path> sourceDirectories;
        final boolean dryRun;
        String status = "RUNNING";
        BukkitTask task;
        int worldPosition, chunkPosition, directoryPosition;
        Chunk[] chunks;
        Iterator<Entity> entities;
        DirectoryStream<Path> stream;
        Iterator<Path> files;
        Path currentDirectory;
        boolean entitiesDone, recordsDone;
        CompletableFuture<Boolean> pendingCopy;
        Path pendingPath;
        int processed, copied, skipped, failed;
        Job(String id, List<World> worlds, List<Path> sourceDirectories, boolean dryRun) {
            this.id = id;
            this.worlds = worlds;
            this.sourceDirectories = sourceDirectories;
            this.dryRun = dryRun;
        }
        void openDirectory(Path path) throws IOException {
            currentDirectory = path;
            stream = Files.newDirectoryStream(path, "*.bin");
            files = stream.iterator();
        }
        void closeDirectory() {
            if (stream != null) {
                try { stream.close(); } catch (IOException ignored) { }
            }
            stream = null;
            files = null;
            currentDirectory = null;
        }
        String message() {
            return "WCL-MIGRATION job=" + id + " status=" + status
                    + " processed=" + processed + " copied=" + copied
                    + " skipped=" + skipped + " failed=" + failed;
        }
    }
}
