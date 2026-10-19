package co.surumene.whatawonderfulchicken.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Bounded, main-thread migration of only the currently loaded matching Entities.
 * Does not force-load chunks, mutate external source records, or claim offline coverage.
 */
public final class WorldStorageMigration<E extends Entity> {
    private static final int MAX_PER_TICK = 4;
    private final JavaPlugin plugin;
    private final Class<E> entityType;
    private final Predicate<E> eligible;
    private final BiFunction<E, Boolean, Boolean> migration;
    private Job<E> last;

    public WorldStorageMigration(JavaPlugin plugin, Class<E> entityType,
                                 Predicate<E> eligible,
                                 BiFunction<E, Boolean, Boolean> migration) {
        this.plugin = Objects.requireNonNull(plugin);
        this.entityType = Objects.requireNonNull(entityType);
        this.eligible = Objects.requireNonNull(eligible);
        this.migration = Objects.requireNonNull(migration);
    }

    public int start(CommandSender sender, boolean dryRun) {
        if (last != null && last.status.equals("RUNNING")) {
            sender.sendMessage("WCL-MIGRATION job=busy status=FAILED processed=0 copied=0 skipped=0 failed=1");
            return 0;
        }
        List<E> targets = new ArrayList<>();
        // Enumerate loaded entities only, once per explicit administrator request.
        for (World world : Bukkit.getWorlds()) {
            for (E entity : world.getEntitiesByClass(entityType)) {
                if (eligible.test(entity)) targets.add(entity);
            }
        }
        Job<E> job = new Job<>(UUID.randomUUID().toString(), targets, dryRun);
        last = job;
        sender.sendMessage(job.message());
        job.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> advance(job), 1L, 1L);
        return 1;
    }

    public int status(CommandSender sender) {
        if (last == null) {
            sender.sendMessage("WCL-MIGRATION job=none status=FAILED processed=0 copied=0 skipped=0 failed=1");
            return 0;
        }
        sender.sendMessage(last.message());
        return last.status.equals("SUCCESS") ? 1 : 0;
    }

    private void advance(Job<E> job) {
        if (job != last || !job.status.equals("RUNNING")) return;
        for (int count = 0; count < MAX_PER_TICK && job.index < job.targets.size(); count++) {
            E entity = job.targets.get(job.index++);
            try {
                if (!entity.isValid() || !eligible.test(entity)) {
                    // Unload/removal between snapshot and processing is not a successful migration.
                    job.failed++;
                    continue;
                }
                boolean wouldCopy = migration.apply(entity, job.dryRun);
                if (wouldCopy) job.copied++;
                else job.skipped++;
            } catch (RuntimeException error) {
                job.failed++;
                plugin.getLogger().warning("World payload migration failed for "
                        + entity.getUniqueId() + ": " + error.getMessage());
            }
        }
        if (job.index == job.targets.size()) {
            job.status = job.failed == 0 ? "SUCCESS" : "FAILED";
            job.task.cancel();
            job.task = null;
            plugin.getLogger().info("WCL migration finished: " + job.message());
        }
    }

    private static final class Job<E extends Entity> {
        private final String id;
        private final List<E> targets;
        private final boolean dryRun;
        private int index;
        private int copied;
        private int skipped;
        private int failed;
        private String status = "RUNNING";
        private BukkitTask task;

        private Job(String id, List<E> targets, boolean dryRun) {
            this.id = id;
            this.targets = targets;
            this.dryRun = dryRun;
        }

        private String message() {
            return "WCL-MIGRATION job=" + id + " status=" + status
                    + " processed=" + index + " copied=" + copied
                    + " skipped=" + skipped + " failed=" + failed;
        }
    }
}
