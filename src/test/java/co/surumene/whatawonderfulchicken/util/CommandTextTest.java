package co.surumene.whatawonderfulchicken.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandTextTest {
    @Test
    void extractsTwoCompoundsWithQuotedBraces() {
        assertEquals(List.of("{stats:{size:2}}", "{CustomName:'{bird}'}"),
                CommandText.extractTopLevelCompounds("{stats:{size:2}} {CustomName:'{bird}'}"));
    }
}