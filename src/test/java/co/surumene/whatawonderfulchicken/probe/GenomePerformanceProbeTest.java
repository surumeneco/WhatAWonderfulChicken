package co.surumene.whatawonderfulchicken.probe;

import co.surumene.whatawonderfulchicken.breeding.*;
import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.whatawonderfulchicken.founder.*;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.wgl.api.*;
import co.surumene.wgl.core.*;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class GenomePerformanceProbeTest {
    private final WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
    private final WonderfulChickenGenomeProfile profile = new WonderfulChickenGenomeProfile(
        WonderfulChickenGenomeSettings.defaults(), WonderfulChickenSynthesisSettings.defaults(),
        engine.geneSequenceCodec());
    private final WonderfulChickenFounderSynthesizer founder = new WonderfulChickenFounderSynthesizer(engine, profile);
    private final WonderfulChickenBreedingService breeding = new WonderfulChickenBreedingService(() -> engine, () -> profile);
    private final ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    record Timed<T>(T result, double millis, double cpuMillis, double allocMiB, String hot) {}

    private <T> Timed<T> time(String stage, long seed, Supplier<T> action) {
        Thread owner = Thread.currentThread();
        long tid = owner.threadId();
        AtomicBoolean running = new AtomicBoolean(true);
        Map<String,Integer> samples = new HashMap<>();
        Thread sampler = new Thread(() -> {
            while (running.get()) {
                for (StackTraceElement frame : owner.getStackTrace()) {
                    if (frame.getClassName().startsWith("co.surumene.wgl.core.")) {
                        String key = frame.getClassName().substring("co.surumene.wgl.core.".length()) + "." + frame.getMethodName();
                        samples.merge(key, 1, Integer::sum);
                        break;
                    }
                }
                try { Thread.sleep(2); } catch (InterruptedException ignored) { break; }
            }
        }, "wgl-probe-sampler");
        sampler.setDaemon(true);
        long startBytes = mx.isThreadAllocatedMemorySupported() ? mx.getThreadAllocatedBytes(tid) : -1;
        long startCpu = mx.isThreadCpuTimeSupported() ? mx.getThreadCpuTime(tid) : -1;
        long start = System.nanoTime();
        sampler.start();
        T result;
        try { result = action.get(); }
        finally { running.set(false); try { sampler.join(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
        double millis = (System.nanoTime() - start)/1e6;
        double cpu = startCpu >= 0 ? (mx.getThreadCpuTime(tid)-startCpu)/1e6 : -1;
        double alloc = startBytes >= 0 ? (mx.getThreadAllocatedBytes(tid)-startBytes)/1048576.0 : -1;
        String hot = samples.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
          .limit(7).map(e -> e.getKey()+"="+e.getValue()).reduce((a,b)->a+";"+b).orElse("-");
        System.out.printf(Locale.ROOT,"PERF species=WWC stage=%s seed=%d wall_ms=%.2f cpu_ms=%.2f alloc_MiB=%.2f samples=%d hotspots=%s%n",
          stage,seed,millis,cpu,alloc,samples.values().stream().mapToInt(Integer::intValue).sum(),hot);
        return new Timed<>(result,millis,cpu,alloc,hot);
    }

    private WonderfulChickenData individual(DiploidGenome genome, FounderOrigin origin) {
        DecodeResult<WonderfulChickenDecodedPhenotype> dec = engine.decode(profile, genome);
        WonderfulChickenData data = new WonderfulChickenData();
        data.genome(genome);
        data.phenotypeSnapshot(dec.phenotype().toSnapshot(dec.identity(),
            origin == FounderOrigin.NATURAL ? PhenotypeOrigin.NATURAL_FOUNDER : PhenotypeOrigin.CHICKEN_TRAP_FOUNDER));
        data.generation(0);
        data.bloodlineId("probe");
        data.pedigree(PedigreeData.EMPTY);
        for (StatType stat : StatType.values()) {
            double v = data.phenotypeSnapshot().normalizedAbilities().get(stat);
            data.normalized(stat,v);
            data.value(stat,v);
        }
        return data;
    }

    private static double score(BitSequence child, ChromosomePair parent, int offset) {
        if (offset+48 > child.bitLength()
          || offset+48 > parent.haplotypeA().bitLength()
          || offset+48 > parent.haplotypeB().bitLength()) return Double.NaN;
        long c = child.toLong(offset,48);
        return Math.min(Long.bitCount(c ^ parent.haplotypeA().toLong(offset,48)),
                        Long.bitCount(c ^ parent.haplotypeB().toLong(offset,48)));
    }

    private static double[] genomeSimilarity(DiploidGenome offspring, DiploidGenome a, DiploidGenome b, DiploidGenome outsider) {
        double familialDistance=0, outsiderDistance=0, exactFamily=0, exactOutsider=0;
        int windows=0;
        for (int i=0;i<offspring.chromosomePairs().size();i++) {
            ChromosomePair child = offspring.chromosomePairs().get(i);
            ChromosomePair ap = a.chromosomePairs().get(i), bp = b.chromosomePairs().get(i), out = outsider.chromosomePairs().get(i);
            for (int side=0;side<2;side++) {
                BitSequence strand = side==0 ? child.haplotypeA() : child.haplotypeB();
                ChromosomePair own = side==0 ? ap : bp;
                for (int pos=0;pos+48<=strand.bitLength();pos+=96) {
                    double f=score(strand,own,pos), u=score(strand,out,pos);
                    if (!Double.isFinite(f) || !Double.isFinite(u)) continue;
                    familialDistance+=f; outsiderDistance+=u;
                    if(f==0) exactFamily++; if(u==0) exactOutsider++;
                    windows++;
                }
            }
        }
        return new double[]{familialDistance/windows, outsiderDistance/windows, exactFamily/windows, exactOutsider/windows, windows};
    }
    private static double mae(Map<StatType,Double> child, Map<StatType,Double> a, Map<StatType,Double> b) {
        double d=0;
        for (StatType s:StatType.values()) d+=Math.abs(child.get(s)-(a.get(s)+b.get(s))*0.5);
        return d/StatType.values().length;
    }

    @Test void executeProductionFounderAndBreedingPaths() {
        List<WonderfulChickenData> parents=new ArrayList<>();
        for (int i=0;i<3;i++) {
            long seed=20261012000L+i;
            FounderOrigin origin=i==2?FounderOrigin.CHICKEN_TRAP:FounderOrigin.NATURAL;
            Timed<FounderGenomeSynthesis> t=time("founder_"+origin,seed,()->founder.synthesize(origin,seed));
            SynthesisResult.Success ok=assertInstanceOf(SynthesisResult.Success.class,t.result().result());
            parents.add(individual(ok.genome(),origin));
        }
        WonderfulChickenData a=parents.get(0), b=parents.get(1), outsider=parents.get(2);
        double familyDist=0, unrelatedDist=0, familyExact=0, unrelatedExact=0, midMae=0, unrelatedMae=0;
        int successes=0;
        for(int i=0;i<3;i++) {
            long seed=20261012200L+i;
            Timed<WonderfulChickenBreedingOutcome> t=time("breeding",seed,()->breeding.breed(
                new BreedingParent("Alpha",a),new BreedingParent("Beta",b),seed));
            WonderfulChickenBreedingOutcome.Success ok=assertInstanceOf(WonderfulChickenBreedingOutcome.Success.class,t.result());
            double[] g=genomeSimilarity(ok.genome(),a.genome(),b.genome(),outsider.genome());
            double p=mae(ok.phenotypeSnapshot().normalizedAbilities(),
                a.phenotypeSnapshot().normalizedAbilities(),b.phenotypeSnapshot().normalizedAbilities());
            double u=mae(ok.phenotypeSnapshot().normalizedAbilities(),
                outsider.phenotypeSnapshot().normalizedAbilities(),outsider.phenotypeSnapshot().normalizedAbilities());
            System.out.printf(Locale.ROOT,"HEREDITY species=WWC child=%d family_bit_error_48=%.4f unrelated_bit_error_48=%.4f family_exact48=%.5f unrelated_exact48=%.5f windows=%d phenotype_midparent_MAE=%.5f phenotype_unrelated_MAE=%.5f%n",
                i,g[0],g[1],g[2],g[3],(int)g[4],p,u);
            familyDist+=g[0];unrelatedDist+=g[1];familyExact+=g[2];unrelatedExact+=g[3];midMae+=p;unrelatedMae+=u;successes++;
        }
        System.out.printf(Locale.ROOT,"HEREDITY_SUMMARY species=WWC children=%d mean_family_bit_error_48=%.4f mean_unrelated_bit_error_48=%.4f family_exact48=%.5f unrelated_exact48=%.5f mean_midparent_MAE=%.5f mean_unrelated_MAE=%.5f%n",
            successes,familyDist/successes,unrelatedDist/successes,familyExact/successes,unrelatedExact/successes,midMae/successes,unrelatedMae/successes);
    }
}
