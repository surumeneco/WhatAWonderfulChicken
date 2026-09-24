package co.surumene.whatawonderfulchicken.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnbtLikeParserTest {
    @Test
    void parsesNestedCompoundAndNumbers() {
        Map<String, Object> root = new SnbtLikeParser("{stats:{ground_speed:12.5,size:2},behavior:{mode:'follow'},baby:true}").parseCompound();
        assertEquals(Boolean.TRUE, root.get("baby"));
        @SuppressWarnings("unchecked") Map<String, Object> stats = (Map<String, Object>) root.get("stats");
        assertEquals(12.5, ((Number) stats.get("ground_speed")).doubleValue(), 0.0001);
        assertEquals(2L, stats.get("size"));
    }

    @Test
    void rejectsUnclosedCompound() {
        assertThrows(IllegalArgumentException.class, () -> new SnbtLikeParser("{stats:{size:2}").parseCompound());
    }
}