package co.surumene.whatawonderfulchicken.runtime;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import java.util.Objects;
import java.util.logging.Logger;

/** Compatibility entrypoint. WWC/WWW still register their own handler once. */
public final class BiologicalClockListener implements Listener {
    private final co.surumene.wcl.plugin.time.BiologicalClockSkipListener delegate;

    public BiologicalClockListener(BiologicalClock clock, Logger logger) {
        Objects.requireNonNull(clock, "clock");
        delegate = new co.surumene.wcl.plugin.time.BiologicalClockSkipListener(
                clock::worldName, clock::recordIgnoredSkip, logger);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        delegate.onTimeSkip(event);
    }
}
