package co.surumene.genomestats;

import co.surumene.wgl.api.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Isolated diagnostic harness; no production-code changes. */
public final class GenomeStats {
    public static final int SHARD = 0; // Golden comparison: same seed as marker-inheritance-00.
    public static final int FAMILIES = 2;
    public static final int SIBLINGS = 5;
    private static final ThreadMXBean MX = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private GenomeStats() {}

    private static int shardFromRef() {
        String name = System.getenv().getOrDefault("GITHUB_REF_NAME", "feature/genome-stats-00");
        try { return Integer.parseInt(name.substring(name.lastIndexOf('-') + 1)); }
        catch (RuntimeException e) { return 0; }
    }

    public static long seed(int family, int event) {
        return 202610120000L + SHARD * 100000L + family * 1000L + event;
    }

    public static <T> T timed(String species, String stage, int family, long seed, Supplier<T> task) {
        Thread thread = Thread.currentThread();
        long id = thread.threadId();
        AtomicBoolean active = new AtomicBoolean(true);
        Map<String,Integer> samples = new HashMap<>();
        Thread sampler = new Thread(() -> {
            while (active.get()) {
                for (StackTraceElement f : thread.getStackTrace()) {
                    if (f.getClassName().startsWith("co.surumene.wgl.core.")) {
                        String key = f.getClassName().substring("co.surumene.wgl.core.".length()) + "." + f.getMethodName();
                        samples.merge(key, 1, Integer::sum);
                        break;
                    }
                }
                try { Thread.sleep(2); } catch (InterruptedException e) { break; }
            }
        }, "wgl-stat-sampler");
        sampler.setDaemon(true);
        long beforeAlloc = MX.isThreadAllocatedMemorySupported() ? MX.getThreadAllocatedBytes(id) : -1L;
        long beforeCpu = MX.isThreadCpuTimeSupported() ? MX.getThreadCpuTime(id) : -1L;
        long beforeTime = System.nanoTime();
        sampler.start();
        T result;
        String status = "OK";
        try { result = task.get(); }
        catch (RuntimeException | Error t) { status="EXCEPTION_"+t.getClass().getSimpleName(); throw t; }
        finally {
            active.set(false);
            try { sampler.join(1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            double wallMs = (System.nanoTime()-beforeTime)/1e6;
            double cpuMs = beforeCpu >= 0 ? (MX.getThreadCpuTime(id)-beforeCpu)/1e6 : -1.0;
            double allocMiB = beforeAlloc >= 0 ? (MX.getThreadAllocatedBytes(id)-beforeAlloc)/1048576.0 : -1.0;
            String hot = samples.entrySet().stream()
                    .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                    .limit(6).map(e->e.getKey()+":"+e.getValue())
                    .reduce((a,b)->a+";"+b).orElse("-");
            System.out.printf(Locale.ROOT,
              "STAT_PERF species=%s shard=%d family=%d stage=%s seed=%d result=%s wall_ms=%.3f cpu_ms=%.3f allocated_mib=%.3f sample_n=%d hotspots=%s%n",
              species,SHARD,family,stage,seed,status,wallMs,cpuMs,allocMiB,samples.values().stream().mapToInt(Integer::intValue).sum(),hot);
        }
        return result;
    }

    public static void genome(String species,int family,int sibling,DiploidGenome child,
                              DiploidGenome a,DiploidGenome b,DiploidGenome stranger) {
        long windows=0,fDist=0,uDist=0,fExact=0,uExact=0;
        long totalBits=0,parentBits=0; int lenChanged=0;
        for(int chr=0;chr<child.chromosomePairs().size();chr++) {
            ChromosomePair c=child.chromosomePairs().get(chr),
              ap=a.chromosomePairs().get(chr),bp=b.chromosomePairs().get(chr),up=stranger.chromosomePairs().get(chr);
            for(int side=0;side<2;side++) {
                BitSequence ch=side==0?c.haplotypeA():c.haplotypeB();
                ChromosomePair own=side==0?ap:bp;
                totalBits+=ch.bitLength(); parentBits+=own.haplotypeA().bitLength();
                if(ch.bitLength()!=own.haplotypeA().bitLength() && ch.bitLength()!=own.haplotypeB().bitLength())lenChanged++;
                int len=Math.min(ch.bitLength(), Math.min(Math.min(own.haplotypeA().bitLength(),own.haplotypeB().bitLength()),
                              Math.min(up.haplotypeA().bitLength(),up.haplotypeB().bitLength())));
                for(int pos=0;pos+48<=len;pos+=96) {
                    long w=ch.toLong(pos,48);
                    int fd=Math.min(Long.bitCount(w^own.haplotypeA().toLong(pos,48)),
                                    Long.bitCount(w^own.haplotypeB().toLong(pos,48)));
                    int ud=Math.min(Long.bitCount(w^up.haplotypeA().toLong(pos,48)),
                                    Long.bitCount(w^up.haplotypeB().toLong(pos,48)));
                    windows++; fDist+=fd; uDist+=ud;
                    if(fd==0)fExact++;if(ud==0)uExact++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
         "STAT_GENOME species=%s shard=%d family=%d sibling=%d windows=%d family_error_48=%.5f unrelated_error_48=%.5f exact_family=%.6f exact_unrelated=%.6f length_changed_lanes=%d total_child_bits=%d%n",
         species,SHARD,family,sibling,windows,fDist/(double)windows,uDist/(double)windows,
         fExact/(double)windows,uExact/(double)windows,lenChanged,totalBits);
    }

    public static <K extends Enum<K>> void phenotype(String species,int family,int sibling,String group,
           Map<K,Double> a,Map<K,Double> b,Map<K,Double> child,Map<K,Double> unrelated) {
        for(K key:child.keySet()) {
            double av=a.get(key),bv=b.get(key),cv=child.get(key),uv=unrelated.get(key);
            System.out.printf(Locale.ROOT,
             "STAT_PHENO species=%s shard=%d family=%d sibling=%d group=%s feature=%s parent_a=%.7f parent_b=%.7f midpoint=%.7f child=%.7f unrelated=%.7f%n",
             species,SHARD,family,sibling,group,key.name(),av,bv,(av+bv)/2.0,cv,uv);
        }
    }

    public static void categorical(String species,int family,int sibling,
            Enum<?> a,Enum<?> b,Enum<?> child,Enum<?> unrelated,
            Collection<String> aTraits,Collection<String> bTraits,
            Collection<String> childTraits,Collection<String> unrelatedTraits) {
        Set<String> parentSet=new HashSet<>(aTraits);parentSet.addAll(bTraits);
        Set<String> c=new HashSet<>(childTraits);
        Set<String> u=new HashSet<>(unrelatedTraits);
        long overlap=c.stream().filter(parentSet::contains).count();
        long unrelatedOverlap=u.stream().filter(parentSet::contains).count();
        System.out.printf(Locale.ROOT,
         "STAT_CATEGORY species=%s shard=%d family=%d sibling=%d parent_a=%s parent_b=%s child=%s unrelated=%s same_parent=%s same_unrelated=%s child_traits=%d parent_union_traits=%d child_parent_overlap=%d unrelated_parent_overlap=%d%n",
         species,SHARD,family,sibling,a.name(),b.name(),child.name(),unrelated.name(),
         (child==a||child==b), (unrelated==a||unrelated==b),c.size(),parentSet.size(),overlap,unrelatedOverlap);
    }
}
