package co.surumene.whatawonderfulchicken.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityScanPolicyTest {
    @Test
    void candidateOnlyModeRetainsKnownPdcChecksButOmitsGlobalScan() {
        assertTrue(CompatibilityScanPolicy.shouldScanKnownCandidates(
                CompatibilityScanPolicy.Mode.CANDIDATE_ONLY));
        assertFalse(CompatibilityScanPolicy.shouldScanAllChunks(
                CompatibilityScanPolicy.Mode.CANDIDATE_ONLY));
        assertTrue(CompatibilityScanPolicy.shouldScanAllChunks(
                CompatibilityScanPolicy.Mode.ALL_LOADED_CHUNKS));
    }
}
