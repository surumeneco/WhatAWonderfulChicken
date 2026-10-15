package co.surumene.whatawonderfulchicken.gui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChickenPedigreeLineTest {
    @Test void parentsAndGrandparentsUseSameCompactInfoRow() {
        assertEquals("無名の鶏(2) a1b2 まじめ 直伝/見張り番",
                ChickenPedigreeLine.format("無名の鶏", 2, "a1b2", "まじめ",
                        List.of("直伝", "見張り番"), "無し"));
        assertEquals("祖母(1) ff00 おっとり 無し",
                ChickenPedigreeLine.format("祖母", 1, "ff00", "おっとり",
                        List.of(), "無し"));
    }
    @Test void missingPhenotypeIsUnknownInsteadOfAssumedNoTraits() {
        assertEquals("祖父(0) ffff 不明 不明",
                ChickenPedigreeLine.format("祖父", 0, "ffff", null,
                        List.of(), "不明"));
    }
}
