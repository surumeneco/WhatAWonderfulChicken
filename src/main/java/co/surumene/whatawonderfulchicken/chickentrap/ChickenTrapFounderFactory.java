package co.surumene.whatawonderfulchicken.chickentrap;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.whatawonderfulchicken.founder.*;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.wgl.api.DecodeResult;
import co.surumene.wgl.api.SynthesisResult;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** WGL is authoritative for the higher-capability Chicken Trap founder. */
public final class ChickenTrapFounderFactory {
    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenStore store;
    private final ConfigService config;

    public ChickenTrapFounderFactory(WhatAWonderfulChickenPlugin plugin,
                                    WonderfulChickenStore store, ConfigService config) {
        this.plugin = plugin;
        this.store = store;
        this.config = config;
    }

    public WonderfulChickenData generate() {
        var engine = plugin.genomeLib().engine();
        var synthesizer = new WonderfulChickenFounderSynthesizer(engine, plugin.genomeProfile());
        var synthesis = synthesizer.synthesize(FounderOrigin.CHICKEN_TRAP,
                ThreadLocalRandom.current().nextLong());
        if (!(synthesis.result() instanceof SynthesisResult.Success success)) {
            var failure = (SynthesisResult.Failure) synthesis.result();
            throw new IllegalStateException("Trap founder synthesis failed: "
                    + failure.reason() + " / " + failure.detail());
        }
        DecodeResult<WonderfulChickenDecodedPhenotype> decoded =
                engine.decode(plugin.genomeProfile(), success.genome());
        var phenotype = decoded.phenotype().toSnapshot(
                decoded.identity(), PhenotypeOrigin.CHICKEN_TRAP_FOUNDER);

        WonderfulChickenData data = new WonderfulChickenData();
        for (StatType stat : StatType.values()) {
            double normalized = phenotype.normalizedAbilities().get(stat);
            data.normalized(stat, normalized);
            data.value(stat, store.toValue(stat, normalized));
        }
        data.genome(success.genome());
        data.phenotypeSnapshot(phenotype);
        data.adultBiologicalTime(0L);
        data.currentStamina(data.value(StatType.STAMINA));
        data.bloodlineId(UUID.randomUUID().toString());
        data.generation(0);
        data.pedigree(PedigreeData.EMPTY);
        data.genetics(Genetics.random());
        data.currentStamina(data.effective(StatType.STAMINA, config.natureAdjustment()));
        return data;
    }
}
