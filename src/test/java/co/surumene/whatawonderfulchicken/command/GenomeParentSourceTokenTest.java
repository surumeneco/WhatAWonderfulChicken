package co.surumene.whatawonderfulchicken.command;

import co.surumene.wgl.api.*;
import co.surumene.wgl.core.EngineConfig;
import co.surumene.wgl.core.WonderfulGenomeEngine;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class GenomeParentSourceTokenTest {
    private final GenomeEngine engine=WonderfulGenomeEngine.create(EngineConfig.defaults());

    @Test void diploidSourceRoundTripsThroughWglp() {
        DiploidGenome genome=GenomeAdminCodec.parseGenome(
                GenomeInputParser.Format.BITS,"1010,0101,1010,0101,1010,0101",null,
                engine.sequenceCodec());
        BreedingParentSource source=new BreedingParentSource.DiploidParent(genome);
        String token=GenomeParentSourceToken.encode(engine,source);
        assertTrue(token.startsWith("wglp:"));
        assertEquals(source,GenomeParentSourceToken.decode(engine,token));
    }

    @Test void gameteSourceRetainsItsTypeAndChromosomes() {
        HaploidGenome gamete=new HaploidGenome(1,List.of(
                BitSequence.fromBits("1010"),BitSequence.fromBits("0101")));
        BreedingParentSource source=new BreedingParentSource.Gamete(gamete);
        assertEquals(source,GenomeParentSourceToken.decode(engine,
                GenomeParentSourceToken.encode(engine,source)));
    }

    @Test void rejectsMalformedOrUnboundedPayload() {
        assertThrows(IllegalArgumentException.class,
                ()->GenomeParentSourceToken.decode(engine,"wglp:???"));
        assertThrows(IllegalArgumentException.class,
                ()->GenomeParentSourceToken.decode(engine,"other:ABC"));
        assertThrows(IllegalArgumentException.class,
                ()->GenomeParentSourceToken.decode(engine,
                    "wglp:"+Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(new byte[]{1,2,3,4})));
    }
}
