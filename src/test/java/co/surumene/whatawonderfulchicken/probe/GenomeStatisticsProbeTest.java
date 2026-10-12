package co.surumene.whatawonderfulchicken.probe;

import co.surumene.genomestats.GenomeStats;
import co.surumene.whatawonderfulchicken.breeding.*;
import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.whatawonderfulchicken.founder.*;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.wgl.api.*;
import co.surumene.wgl.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class GenomeStatisticsProbeTest {
    private final WonderfulGenomeEngine engine=WonderfulGenomeEngine.create(EngineConfig.defaults());
    private final WonderfulChickenGenomeProfile profile=new WonderfulChickenGenomeProfile(
      WonderfulChickenGenomeSettings.defaults(),WonderfulChickenSynthesisSettings.defaults(),engine.geneSequenceCodec());
    private final WonderfulChickenFounderSynthesizer founder=new WonderfulChickenFounderSynthesizer(engine,profile);
    private final WonderfulChickenBreedingService breed=new WonderfulChickenBreedingService(()->engine,()->profile);

    private WonderfulChickenData make(FounderGenomeSynthesis generated,FounderOrigin origin) {
        SynthesisResult.Success synthesis=assertInstanceOf(SynthesisResult.Success.class,generated.result());
        DiploidGenome genome=synthesis.genome();
        DecodeResult<WonderfulChickenDecodedPhenotype> canonical=engine.decode(profile,genome);
        PhenotypeSnapshot ph=canonical.phenotype().toSnapshot(canonical.identity(),origin==FounderOrigin.NATURAL?
            PhenotypeOrigin.NATURAL_FOUNDER:PhenotypeOrigin.CHICKEN_TRAP_FOUNDER);
        WonderfulChickenData d=new WonderfulChickenData();
        d.genome(genome); d.phenotypeSnapshot(ph); d.generation(0); d.bloodlineId("stat");
        d.pedigree(PedigreeData.EMPTY);
        for(StatType s:StatType.values()) {double v=ph.normalizedAbilities().get(s);d.normalized(s,v);d.value(s,v);}
        return d;
    }
    private static List<String> traits(PhenotypeSnapshot ph) {
        return ph.expressedTraits().stream().map(t->t.trait().name()).toList();
    }

    @Test void executeStatisticalCohort() {
        System.out.printf("STAT_META species=WWC shard=%d families=%d siblings_per_family=%d%n",
          GenomeStats.SHARD,GenomeStats.FAMILIES,GenomeStats.SIBLINGS);
        List<WonderfulChickenData> parents=new ArrayList<>();
        for(int f=0;f<GenomeStats.FAMILIES;f++){
            for(int side=0;side<2;side++){
                final int family=f, s=side;long seed=GenomeStats.seed(f,1+side);
                FounderGenomeSynthesis generated=GenomeStats.timed("WWC","NATURAL",f,seed,
                  ()->founder.synthesize(FounderOrigin.NATURAL,seed));
                SynthesisResult.Success success=assertInstanceOf(SynthesisResult.Success.class,generated.result());
                parents.add(make(generated,FounderOrigin.NATURAL));
                System.out.printf("STAT_SUCCESS species=WWC stage=NATURAL shard=%d family=%d side=%d status=success%n",
                   GenomeStats.SHARD,family,s);
                if(side==0) GenomeStats.timed("WWC","DECODE",f,seed,()->engine.decode(profile,success.genome()));
            }
            long seed=GenomeStats.seed(f,90);
            FounderGenomeSynthesis trap=GenomeStats.timed("WWC","TRAP",f,seed,
              ()->founder.synthesize(FounderOrigin.CHICKEN_TRAP,seed));
            if(trap.result() instanceof SynthesisResult.Failure fail) {
                System.out.printf("STAT_FAILURE species=WWC stage=TRAP shard=%d family=%d reason=%s%n",
                  GenomeStats.SHARD,f,fail.reason());
            } else System.out.printf("STAT_SUCCESS species=WWC stage=TRAP shard=%d family=%d status=success%n",
                  GenomeStats.SHARD,f);
        }
        int successTotal=0;
        for(int f=0;f<GenomeStats.FAMILIES;f++) {
            WonderfulChickenData a=parents.get(f*2),b=parents.get(f*2+1),
                unrelated=parents.get((1-f)*2);
            for(int i=0;i<GenomeStats.SIBLINGS;i++){
                final int family=f, sibling=i;long seed=GenomeStats.seed(f,200+i);
                WonderfulChickenBreedingOutcome result=GenomeStats.timed("WWC","BREED",f,seed,
                  ()->breed.breed(new BreedingParent("A",a),new BreedingParent("B",b),seed));
                if(!(result instanceof WonderfulChickenBreedingOutcome.Success child)){
                    System.out.printf("STAT_FAILURE species=WWC stage=BREED shard=%d family=%d sibling=%d reason=%s%n",
                      GenomeStats.SHARD,f,i,result);
                    continue;
                }
                successTotal++;
                PhenotypeSnapshot c=child.phenotypeSnapshot(),ap=a.phenotypeSnapshot(),
                   bp=b.phenotypeSnapshot(),up=unrelated.phenotypeSnapshot();
                GenomeStats.genome("WWC",f,i,child.genome(),a.genome(),b.genome(),unrelated.genome());
                GenomeStats.phenotype("WWC",f,i,"ABILITY",ap.normalizedAbilities(),bp.normalizedAbilities(),
                  c.normalizedAbilities(),up.normalizedAbilities());
                GenomeStats.phenotype("WWC",f,i,"PERSONALITY_FACTOR",ap.personalityFactors(),bp.personalityFactors(),
                  c.personalityFactors(),up.personalityFactors());
                GenomeStats.phenotype("WWC",f,i,"DEVELOPMENT",ap.developmentFactors(),bp.developmentFactors(),
                  c.developmentFactors(),up.developmentFactors());
                GenomeStats.categorical("WWC",f,i,ap.personality(),bp.personality(),c.personality(),up.personality(),
                  traits(ap),traits(bp),traits(c),traits(up));
                System.out.printf(Locale.ROOT,"STAT_GENETIC species=WWC shard=%d family=%d sibling=%d divine=%s injuries=%d%n",
                 GenomeStats.SHARD,f,i,c.divineLineageExpressed(),c.injuries().size());
            }
        }
        System.out.printf("STAT_COHORT_END species=WWC shard=%d breed_success=%d attempted=%d%n",
             GenomeStats.SHARD,successTotal,GenomeStats.FAMILIES*GenomeStats.SIBLINGS);
        assertTrue(successTotal>0);
    }
}
