package co.surumene.whatawonderfulchicken.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Chicken;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class WorldStorageMigrationTest {
    @TempDir Path temporary;

    @Test void sweepsRecordsInBoundedTicksWithoutLoadingOfflinePlayerInventories() throws Exception {
        Path records = Files.createDirectory(temporary.resolve("genomes"));
        Files.createFile(records.resolve("123e4567-e89b-12d3-a456-426614174000.bin"));
        World world = Mockito.mock(World.class);
        Mockito.when(world.getLoadedChunks()).thenReturn(new Chunk[0]);
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        CommandSender sender = Mockito.mock(CommandSender.class);
        BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class);
        BukkitTask scheduled = Mockito.mock(BukkitTask.class);
        List<Runnable> timers = new ArrayList<>();
        Mockito.when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
                .thenAnswer(invocation -> {
                    timers.add(invocation.getArgument(1));
                    return scheduled;
                });
        List<WorldStorageMigration.FileRecord> seen = new ArrayList<>();
        try (var statics = Mockito.mockStatic(Bukkit.class)) {
            statics.when(Bukkit::getWorlds).thenReturn(List.of(world));
            statics.when(Bukkit::getScheduler).thenReturn(scheduler);
            var migration = new WorldStorageMigration<>(plugin, Chicken.class,
                    entity -> true, (entity, dryRun) -> false,
                    () -> List.of(records), (record, dryRun) -> {
                        assertFalse(dryRun);
                        seen.add(record);
                        return true;
                    });
            assertEquals(1, migration.start(sender, false));
            assertEquals(1, timers.size());
            for (int i = 0; i < 5; i++) timers.getFirst().run();
            assertEquals(1, seen.size());
            Mockito.verify(scheduled).cancel();
            assertEquals(1, migration.status(sender));
        }
    }
}
