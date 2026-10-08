package co.surumene.whatawonderfulchicken.command;

import co.surumene.wgl.api.*;
import co.surumene.wgl.core.EngineConfig;
import co.surumene.wgl.core.WonderfulGenomeEngine;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GenomeParentSourceArgumentTest {
    private final WonderfulGenomeEngine engine=WonderfulGenomeEngine.create(EngineConfig.defaults());

    @Test void typedDiploidAndGameteRoundTripWithoutLosingKind() {
        var a=BitSequence.fromBits("1010");
        var b=BitSequence.fromBits("0101");
        var diploid=new BreedingParentSource.DiploidParent(
            new DiploidGenome(1,List.of(new ChromosomePair(a,b))));
        var gamete=new BreedingParentSource.Gamete(new HaploidGenome(1,List.of(a)));
        String d=GenomeParentSourceArgument.encode(diploid,engine);
        String g=GenomeParentSourceArgument.encode(gamete,engine);
        assertTrue(d.startsWith("diploid:"));
        assertTrue(g.startsWith("gamete:"));
        assertEquals(diploid,GenomeParentSourceArgument.parse(d,engine));
        assertEquals(gamete,GenomeParentSourceArgument.parse(g,engine));
    }

    @Test void rejectsWrongTypeAndMalformedInput() {
        var gamete=new BreedingParentSource.Gamete(new HaploidGenome(1,List.of(BitSequence.fromBits("1010"))));
        String valid=GenomeParentSourceArgument.encode(gamete,engine);
        assertThrows(IllegalArgumentException.class,()->GenomeParentSourceArgument.parse("diploid:"+valid.substring(valid.indexOf(':')+1),engine));
        assertThrows(IllegalArgumentException.class,()->GenomeParentSourceArgument.parse("gamete:no*base64",engine));
        assertThrows(IllegalArgumentException.class,()->GenomeParentSourceArgument.parse("raw:123",engine));
    }

    @Test void parentCommandRequiresTwoTypedTokens() {
        var pair=GenomeParentSourceArgument.splitPair("diploid:abc parent gamete:def");
        assertEquals("diploid:abc",pair.first());
        assertEquals("gamete:def",pair.second());
        assertThrows(IllegalArgumentException.class,()->GenomeParentSourceArgument.splitPair("diploid:abc gamete:def"));
        assertThrows(IllegalArgumentException.class,()->GenomeParentSourceArgument.splitPair("diploid:abc parent gamete:def parent diploid:ghi"));
    }
}
