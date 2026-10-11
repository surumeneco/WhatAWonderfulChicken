package co.surumene.whatawonderfulchicken.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GenomePayloadFilesTest {
    @TempDir Path directory;

    @Test
    void recordIsImmutableAndSurvivesReopening() {
        GenomePayloadFiles store = new GenomePayloadFiles(directory);
        byte[] genome = new byte[60000];
        byte[] phenotype = new byte[15000];
        Arrays.fill(genome, (byte) 42);
        Arrays.fill(phenotype, (byte) 17);
        UUID first = store.writeOrReuse(null, genome, phenotype);
        genome[0] = -1;
        phenotype[0] = -1;

        GenomePayloadFiles.Payload loaded = new GenomePayloadFiles(directory).read(first);
        assertEquals(42, loaded.genome()[0]);
        assertEquals(17, loaded.phenotype()[0]);
        assertEquals(first, store.writeOrReuse(first, loaded.genome(), loaded.phenotype()));

        byte[] changed = loaded.genome();
        changed[0] ^= 1;
        UUID second = store.writeOrReuse(first, changed, loaded.phenotype());
        assertNotEquals(first, second);
        assertEquals(42, store.read(first).genome()[0]);
        assertEquals(43, store.read(second).genome()[0]);
    }

    @Test
    void missingAndCorruptPayloadsFailClosed() throws Exception {
        GenomePayloadFiles files = new GenomePayloadFiles(directory);
        assertThrows(IllegalStateException.class, () -> files.read(UUID.randomUUID()));
        UUID reference = files.writeOrReuse(null, new byte[]{1,2,3}, new byte[]{4,5});
        Path path = directory.resolve(reference + ".bin");
        byte[] bytes = Files.readAllBytes(path);
        bytes[16] ^= 1;
        Files.write(path, bytes);
        assertThrows(IllegalStateException.class, () -> new GenomePayloadFiles(directory).read(reference));
    }

    @Test
    void doesNotPublishReferenceOnFailedWrites() throws Exception {
        Path notDirectory = directory.resolve("regular-file");
        Files.writeString(notDirectory, "blocked");
        GenomePayloadFiles files = new GenomePayloadFiles(notDirectory.resolve("genomes"));
        assertThrows(IllegalStateException.class,
                () -> files.writeOrReuse(null, new byte[]{1}, new byte[]{2}));
    }
}
