package co.surumene.whatawonderfulchicken.runtime;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class BiologicalClockListenerDelegationTest {
    @Test void consumerRetainsWorldFilterAndSkipSemantics() throws Exception {
        AtomicLong raw = new AtomicLong(1000);
        BiologicalClockStateStore state = new BiologicalClockStateStore() {
            private BiologicalClockState value;
            @Override public BiologicalClockState load() { return value; }
            @Override public void save(BiologicalClockState v) { value = v; }
        };
        BiologicalClock clock = BiologicalClock.start("world", raw::get, state);
        var listener = new BiologicalClockListener(clock, Logger.getLogger("test"));
        var annotation = listener.getClass().getMethod("onTimeSkip", TimeSkipEvent.class)
                .getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, annotation.priority());
        assertTrue(annotation.ignoreCancelled());

        World resource = Mockito.mock(World.class);
        Mockito.when(resource.getName()).thenReturn("resource");
        TimeSkipEvent other = Mockito.mock(TimeSkipEvent.class);
        Mockito.when(other.getWorld()).thenReturn(resource);
        Mockito.when(other.getSkipReason()).thenReturn(ClockTimeSkipEvent.SkipReason.COMMAND);
        Mockito.when(other.getSkipAmount()).thenReturn(100L);
        listener.onTimeSkip(other);
        assertEquals(1000, clock.currentTime());

        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        TimeSkipEvent applied = Mockito.mock(TimeSkipEvent.class);
        Mockito.when(applied.getWorld()).thenReturn(world);
        Mockito.when(applied.getSkipReason()).thenReturn(ClockTimeSkipEvent.SkipReason.COMMAND);
        Mockito.when(applied.getSkipAmount()).thenReturn(100L);
        listener.onTimeSkip(applied);
        raw.addAndGet(100);
        assertEquals(1000, clock.currentTime());
    }
}
