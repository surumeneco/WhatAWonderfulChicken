package co.surumene.whatawonderfulchicken.persistence;
import co.surumene.wcl.core.runtime.DefaultConsumerContext;
import co.surumene.wcl.core.persistence.WgpfRecordStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class WorldGenomePayloadsTest {
    @TempDir Path root;

    @Test void writesBothWorldsAndMirrorsExistingRecord() {
        World current = Mockito.mock(World.class);
        World primary = Mockito.mock(World.class);
        Mockito.when(current.getWorldFolder()).thenReturn(root.resolve("resource").toFile());
        Mockito.when(primary.getWorldFolder()).thenReturn(root.resolve("world").toFile());
        try (var mocks = Mockito.mockStatic(Bukkit.class)) {
            mocks.when(() -> Bukkit.getWorld("world")).thenReturn(primary);
            mocks.when(Bukkit::getWorlds).thenReturn(List.of(primary, current));
            var records = new WorldGenomePayloads(new DefaultConsumerContext(), "wwc");
            UUID id = records.writeOrReuse(current, null, null, new byte[]{1}, new byte[]{2});
            Path primaryFile = WorldGenomePayloads.worldDirectory(primary, "wwc").resolve(id + ".bin");
            assertTrue(Files.isRegularFile(primaryFile));
            assertArrayEquals(new byte[]{1}, records.read(current, null, id).genome());
        }
    }

    @Test void dryRunDoesNotWriteButActualMigrationIsIdempotent() {
        World current = Mockito.mock(World.class);
        World primary = Mockito.mock(World.class);
        Mockito.when(current.getWorldFolder()).thenReturn(root.resolve("resource").toFile());
        Mockito.when(primary.getWorldFolder()).thenReturn(root.resolve("world").toFile());
        try (var mocks = Mockito.mockStatic(Bukkit.class)) {
            mocks.when(() -> Bukkit.getWorld("world")).thenReturn(primary);
            mocks.when(Bukkit::getWorlds).thenReturn(List.of(primary, current));
            var records = new WorldGenomePayloads(new DefaultConsumerContext(), "wwc");
            Path source = WorldGenomePayloads.worldDirectory(current, "wwc");
            UUID id = new WgpfRecordStore(source).writeOrReuse(null,new byte[]{1},new byte[]{2});
            Path dest = WorldGenomePayloads.worldDirectory(primary, "wwc").resolve(id + ".bin");
            assertTrue(records.mirrorExisting(source, id, true));
            assertFalse(Files.exists(dest));
            assertTrue(records.mirrorExisting(source, id, false));
            assertFalse(records.mirrorExisting(source, id, false));
        }
    }
}
