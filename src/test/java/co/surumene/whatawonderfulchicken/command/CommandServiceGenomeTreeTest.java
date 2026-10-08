package co.surumene.whatawonderfulchicken.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CommandServiceGenomeTreeTest {
    @Test void exposesGenomeGetAndBothSummonPaths() {
        CommandService service=new CommandService(null,null,null,null,null,null);
        var root=service.build();
        assertNotNull(root.getChild("genome"));
        assertNotNull(root.getChild("genome").getChild("get"));
        assertNotNull(root.getChild("genome").getChild("get").getChild("targets"));
        var summon=root.getChild("summon");
        assertNotNull(summon.getChild("genome"));
        assertNotNull(summon.getChild("genome").getChild("format").getChild("haplotypes"));
        assertNotNull(summon.getChild("offspring").getChild("parent").getChild("parents"));
        assertNotNull(summon.getChild("arguments"));
    }
}
