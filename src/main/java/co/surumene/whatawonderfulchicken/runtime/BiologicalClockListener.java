package co.surumene.whatawonderfulchicken.runtime;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.Objects;
import java.util.logging.Logger;

public final class BiologicalClockListener implements Listener {
    private final BiologicalClock clock;
    private final Logger logger;

    public BiologicalClockListener(BiologicalClock clock, Logger logger) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (!event.getWorld().getName().equals(clock.worldName())) {
            return;
        }

        ClockTimeSkipEvent.SkipReason reason = event.getSkipReason();
        if (reason == ClockTimeSkipEvent.SkipReason.COMMAND
                || reason == ClockTimeSkipEvent.SkipReason.CUSTOM) {
            try {
                clock.recordIgnoredSkip(event.getSkipAmount());
            } catch (RuntimeException error) {
                logger.warning(
                        "Could not persist ignored biological time skip; "
                                + "the clock basis was left unchanged: "
                                + error.getMessage());
            }
        }
    }
}
