package co.surumene.whatawonderfulchicken.command;

import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.wgl.api.*;
import java.util.UUID;

public final class GenomeCommandChickenFactory {
    private final WonderfulChickenStore store;
    private final ConfigService config;

    public GenomeCommandChickenFactory(WonderfulChickenStore store, ConfigService config) {
        this.store=store;
        this.config=config;
    }

    public WonderfulChickenData decoded(GenomeEngine engine,
            WonderfulChickenGenomeProfile profile, DiploidGenome genome) {
        DecodeResult<WonderfulChickenDecodedPhenotype> result=engine.decode(profile,genome);
        return fromSnapshot(genome,result.phenotype().toSnapshot(
                result.identity(),PhenotypeOrigin.ADMIN));
    }

    public WonderfulChickenData fromSnapshot(DiploidGenome genome,
            PhenotypeSnapshot snapshot) {
        WonderfulChickenData data=new WonderfulChickenData();
        data.genome(genome);
        data.phenotypeSnapshot(snapshot);
        data.adultBiologicalTime(0L);
        data.genetics(Genetics.random());
        data.pedigree(PedigreeData.EMPTY);
        data.generation(0);
        data.bloodlineId(UUID.randomUUID().toString());
        for(StatType stat:StatType.values()){
            double normalized=snapshot.normalizedAbilities().get(stat);
            data.normalized(stat,normalized);
            data.value(stat,store.toValue(stat,normalized));
        }
        data.currentStamina(data.effective(StatType.STAMINA,config.natureAdjustment()));
        return data;
    }
}
