package co.surumene.whatawonderfulchicken.persistence;

import co.surumene.wcl.api.persistence.ImmutablePayloadStore;
import co.surumene.wcl.api.runtime.ConsumerContext;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owner-side resolver: records follow the actual entity World, not the
 * biological clock or the first-loaded World's storage root.
 * Never deletes legacy files; unmatched missing/corrupt payloads fail closed.
 */
public final class WorldGenomePayloads {
    private final ConsumerContext context;
    private final String species;
    private final Path legacyDirectory;
    private final Map<Path, ImmutablePayloadStore> stores = new HashMap<>();

    public WorldGenomePayloads(ConsumerContext context, String species) {
        this.context = Objects.requireNonNull(context, "context");
        this.species = Objects.requireNonNull(species, "species");
        this.legacyDirectory = worldDirectory(Bukkit.getWorlds().getFirst(), species);
    }

    public static Path worldDirectory(World world, String species) {
        return Objects.requireNonNull(world, "world").getWorldFolder().toPath()
                .resolve("data").resolve("wonderful").resolve(species).resolve("genomes");
    }

    private ImmutablePayloadStore store(Path directory) {
        return stores.computeIfAbsent(directory.toAbsolutePath().normalize(),
                context::newPayloadStore);
    }

    private Path path(Path directory, UUID id) {
        return directory.resolve(id.toString() + ".bin");
    }

    /** Historical references lack a World hint. Search legacy then known worlds. */
    public ImmutablePayloadStore.Payload read(World current, UUID origin, UUID id) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(id, "id");
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        if (origin != null) {
            World recorded = Bukkit.getWorld(origin);
            if (recorded != null) candidates.add(worldDirectory(recorded, species));
        }
        candidates.add(legacyDirectory);
        candidates.add(worldDirectory(current, species));
        for (World world : Bukkit.getWorlds()) candidates.add(worldDirectory(world, species));

        for (Path directory : candidates) {
            if (Files.isRegularFile(path(directory, id))) {
                // A present but corrupt record is an error, not a reason to create a new Genome.
                return store(directory).read(id);
            }
        }
        throw new IllegalStateException("Missing Genome payload " + id
                + " in recorded, legacy, current, or loaded World directories");
    }

    public boolean isLocal(World world, UUID id) {
        return Files.isRegularFile(path(worldDirectory(world, species), id));
    }

    public boolean ensureLocal(World current, UUID origin, UUID id) {
        ImmutablePayloadStore.Payload payload = read(current, origin, id);
        return store(worldDirectory(current, species)).importVerified(id, payload);
    }

    public UUID writeOrReuse(World current, UUID origin, UUID previous, byte[] genome, byte[] phenotype) {
        if (previous != null) ensureLocal(current, origin, previous);
        return store(worldDirectory(current, species)).writeOrReuse(previous, genome, phenotype);
    }
}
