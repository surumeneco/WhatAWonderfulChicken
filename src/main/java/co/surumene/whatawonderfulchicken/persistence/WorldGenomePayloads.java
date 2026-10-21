package co.surumene.whatawonderfulchicken.persistence;

import co.surumene.wcl.api.persistence.ImmutablePayloadStore;
import co.surumene.wcl.api.runtime.ConsumerContext;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Species-owned directory adapter. Every published record is mirrored to the
 * persistent default world so player-owned containers (including offline items)
 * can be restored even after a resource-world regen.
 * WGPF v1 bytes and entity ref UUIDs remain unchanged.
 */
public final class WorldGenomePayloads {
    private final ConsumerContext context;
    private final String species;
    private final World persistentWorld;
    private final Path persistentDirectory;
    private final Map<Path, ImmutablePayloadStore> stores = new ConcurrentHashMap<>();

    public WorldGenomePayloads(ConsumerContext context, String species) {
        this(context, species, Bukkit.getWorld("world"));
    }

    /** Injectable primary World so all World/path decisions can be tested. */
    public WorldGenomePayloads(ConsumerContext context, String species, World persistentWorld) {
        this.context = Objects.requireNonNull(context, "context");
        this.species = Objects.requireNonNull(species, "species");
        this.persistentWorld = Objects.requireNonNull(persistentWorld,
                "Default persistent World 'world' must exist; refusing to select the first loaded World");
        this.persistentDirectory = worldDirectory(persistentWorld, species);
    }

    public static Path worldDirectory(World world, String species) {
        return Objects.requireNonNull(world, "world").getWorldFolder().toPath()
                .resolve("data").resolve("wonderful").resolve(species).resolve("genomes");
    }

    private ImmutablePayloadStore store(Path directory) {
        return stores.computeIfAbsent(directory.toAbsolutePath().normalize(), context::newPayloadStore);
    }

    private static Path file(Path directory, UUID id) {
        return directory.resolve(id + ".bin");
    }

    /** Captures known currently loaded world directories, not a scan of Entity PDC. */
    public List<Path> knownDirectories() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(persistentDirectory);
        for (World world : Bukkit.getWorlds()) roots.add(worldDirectory(world, species));
        return List.copyOf(roots);
    }

    private List<Path> candidateDirectories(World current, UUID origin) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        // The saved world UUID is a hint only. It may disappear after mv regen.
        if (origin != null) {
            World recorded = Bukkit.getWorld(origin);
            if (recorded != null) roots.add(worldDirectory(recorded, species));
        }
        roots.add(worldDirectory(current, species));
        roots.add(persistentDirectory);
        roots.addAll(knownDirectories());
        return new ArrayList<>(roots);
    }

    /** Never synthesize a Genome when an external record is missing or corrupt.
     * Verify all discoverable records with this UUID before selecting a source.
     */
    public ImmutablePayloadStore.Payload read(World current, UUID origin, UUID id) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(id, "id");
        ImmutablePayloadStore.Payload found = null;
        for (Path directory : candidateDirectories(current, origin)) {
            if (!Files.isRegularFile(file(directory, id))) continue;
            ImmutablePayloadStore.Payload candidate = store(directory).read(id);
            if (found != null && (!Arrays.equals(found.genome(), candidate.genome())
                    || !Arrays.equals(found.phenotype(), candidate.phenotype()))) {
                throw new IllegalStateException("Divergent Genome records for UUID " + id
                        + " across World storage directories");
            }
            if (found == null) found = candidate;
        }
        if (found == null) throw new IllegalStateException(
                "Missing Genome payload " + id + " in current/primary/loaded World directories");
        return found;
    }

    public boolean isLocal(World world, UUID id) {
        return Files.isRegularFile(file(worldDirectory(world, species), id));
    }

    private void mirrorPrimary(UUID id, ImmutablePayloadStore.Payload payload) {
        store(persistentDirectory).importVerified(id, payload);
    }

    public boolean ensureLocal(World current, UUID origin, UUID id) {
        ImmutablePayloadStore.Payload payload = read(current, origin, id);
        // Publish player-container-safe record BEFORE any entity PDC reference changes.
        mirrorPrimary(id, payload);
        return store(worldDirectory(current, species)).importVerified(id, payload);
    }

    public UUID writeOrReuse(World current, UUID origin, UUID previous, byte[] genome, byte[] phenotype) {
        if (previous != null) ensureLocal(current, origin, previous);
        ImmutablePayloadStore currentStore = store(worldDirectory(current, species));
        UUID id = currentStore.writeOrReuse(previous, genome, phenotype);
        mirrorPrimary(id, currentStore.read(id));
        return id;
    }

    /** Cover unloaded animals and offline player inventories by scanning immutable
     * .bin records, rather than trying to inspect item/container PDC.
     */
    public boolean mirrorExisting(Path sourceDirectory, UUID id, boolean dryRun) {
        Objects.requireNonNull(sourceDirectory, "sourceDirectory");
        ImmutablePayloadStore.Payload payload = store(sourceDirectory).read(id);
        Path dst = file(persistentDirectory, id);
        if (Files.isRegularFile(dst)) {
            ImmutablePayloadStore.Payload existing = store(persistentDirectory).read(id);
            if (!Arrays.equals(payload.genome(), existing.genome())
                    || !Arrays.equals(payload.phenotype(), existing.phenotype())) {
                throw new IllegalStateException("Primary World Genome UUID collision " + id);
            }
            return false;
        }
        if (!dryRun) mirrorPrimary(id, payload);
        return true;
    }
}
