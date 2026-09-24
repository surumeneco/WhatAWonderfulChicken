package co.surumene.whatawonderfulchicken.util;

import co.surumene.whatawonderfulchicken.data.Rank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankTest {
    @Test
    void extraordinaryRanksUseWildOnlyRanges() {
        assertEquals(Rank.LEGENDARY, Rank.fromNormalized(1.0));
        assertEquals(Rank.MYTHICAL, Rank.fromNormalized(1.01));
        assertEquals(Rank.MYTHICAL, Rank.fromNormalized(1.25));
        assertEquals(Rank.IMPOSSIBLE, Rank.fromNormalized(1.2501));
    }
}