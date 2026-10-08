package co.surumene.whatawonderfulchicken.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class BiologicalClockTest {
    @Test
    void commandAndCustomSkipsAreExcludedWhileNormalProgressRemains() {
        AtomicLong raw = new AtomicLong(100_000L);
        MemoryStore store = new MemoryStore();
        BiologicalClock clock = BiologicalClock.start("world", raw::get, store);

        assertEquals(100_000L, clock.currentTime());

        clock.recordIgnoredSkip(6_000L);
        raw.addAndGet(6_000L);
        assertEquals(100_000L, clock.currentTime());

        raw.addAndGet(240L);
        assertEquals(100_240L, clock.currentTime());
    }

    @Test
    void nightSkipRemainsPartOfBiologicalTime() {
        AtomicLong raw = new AtomicLong(100_000L);
        MemoryStore store = new MemoryStore();
        BiologicalClock clock = BiologicalClock.start("world", raw::get, store);

        raw.addAndGet(6_000L);

        assertEquals(106_000L, clock.currentTime());
    }

    @Test
    void persistedOffsetRestoresTheSameBiologicalTimelineAfterRestart() {
        AtomicLong raw = new AtomicLong(200_000L);
        MemoryStore store = new MemoryStore();
        BiologicalClock clock = BiologicalClock.start("world", raw::get, store);

        clock.recordIgnoredSkip(12_000L);
        raw.addAndGet(12_000L);
        raw.addAndGet(480L);
        assertEquals(200_480L, clock.currentTime());
        clock.persist();

        BiologicalClock restarted = BiologicalClock.start("world", raw::get, store);
        assertEquals(200_480L, restarted.currentTime());
    }

    @Test
    void changingClockWorldRebasesOffsetWithoutChangingBiologicalTime() {
        AtomicLong firstRaw = new AtomicLong(300_000L);
        AtomicLong secondRaw = new AtomicLong(900_000L);
        MemoryStore store = new MemoryStore();
        BiologicalClock clock = BiologicalClock.start("world", firstRaw::get, store);

        firstRaw.addAndGet(1_200L);
        assertEquals(301_200L, clock.currentTime());

        clock.reconfigure("resource", secondRaw::get);

        assertEquals(301_200L, clock.currentTime());
        secondRaw.addAndGet(240L);
        assertEquals(301_440L, clock.currentTime());

        BiologicalClock restarted =
                BiologicalClock.start("resource", secondRaw::get, store);
        assertEquals(301_440L, restarted.currentTime());
    }

    private static final class MemoryStore implements BiologicalClockStateStore {
        private BiologicalClockState state;

        @Override
        public BiologicalClockState load() {
            return state;
        }

        @Override
        public void save(BiologicalClockState state) {
            this.state = state;
        }
    }
}
