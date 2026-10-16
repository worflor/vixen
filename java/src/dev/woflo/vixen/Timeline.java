package dev.woflo.vixen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An immutable timeline of Vixen (vixen.mjs {@code timeline}), with no lineage-depth cap (work item V1): forks and influences are unlimited,
 * and the work of a sample is bounded by the handoffs and windows that are open at the coordinate, never by the depth.
 *
 * <ul>
 * <li>A fork's prefix is its parent's, bitwise; its future leaf's seed derives from the leaf it leaves ({@link Hash#forkSeed}), and it joins
 * the parent over a {@code smooth5} handoff, so the join is C2.</li>
 * <li>A sample past the last handoff reads one leaf only; a history query finds its segment by a binary search over the fork coordinates
 * and mixes only the handoffs open at the coordinate.</li>
 * <li>Influences apply after every fork, in order of their attachment, and are transparent once released (V3).</li>
 * <li>A tail ({@link Tail}, V2) folds what has closed, and a timeline restored from it samples bitwise as the full lineage for every
 * coordinate at or after its checkpoint.</li>
 * </ul>
 * Lineage and influences are persistent append-only vectors: a fork shares its parent's storage when it extends the parent's tip, so n forks
 * cost O(n) in all.
 */
public final class Timeline {
    public static final String COHERENT_ALGORITHM="vixen-hyperfield-c2/5";
    /** One recorded fork: where, why, how wide the join, and the seed of the future it begins. */
    public record Entry(double at, String operation, double handoffWidth, int seed) {
        public double end() {
            return at + handoffWidth;
        }
    }

    /** A persistent append-only vector. */
    private static final class Chain<T> {
        private volatile Object[] data = new Object[8];

        @SuppressWarnings("unchecked")
        T get(int i) {
            return (T) data[i];
        }

        private int fill;

        /** Appends {@code item} after the first {@code n} elements: in place when this chain's tip is {@code n}, else into a copy. */
        synchronized Chain<T> append(int n, T item) {
            if (n == fill) {
                if (n + 1 > data.length) {
                    Object[] bigger = new Object[data.length * 2];
                    System.arraycopy(data, 0, bigger, 0, n);
                    data = bigger;
                }
                data[n] = item;
                fill = n + 1;
                return this;
            }
            Chain<T> copy = new Chain<>();
            Object[] fresh = new Object[Math.max(8, (n + 1) * 2)];
            System.arraycopy(data, 0, fresh, 0, n);
            fresh[n] = item;
            copy.data = fresh;
            copy.fill = n + 1;
            return copy;
        }
    }

    private record LayerJump(int ancestor,double minStart,double maxEnd) {}
    private record Layer(Influence influence,double prefixEnd,double prefixStart,double latestWrite,List<LayerJump> jumps) {}

    /** What every timeline of one lineage shares. */
    private static final class Context {
        final Config leafConfig;
        final boolean coherent;
        final AtomicLong work = new AtomicLong();
        final AtomicLong influenceReads = new AtomicLong();
        final Map<Integer, Leaf> leaves = new LinkedHashMap<>(16, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Leaf> eldest) {
                return size() > 24;
            }
        };

        Context(Config leafConfig) { this(leafConfig,false); }
        Context(Config leafConfig,boolean coherent) {
            this.leafConfig = leafConfig;this.coherent=coherent;
        }

        synchronized Leaf leaf(int seed) {
            Leaf l = leaves.get(seed);
            if (l == null) {
                l = Leaf.create(leafConfig.withSeed(Seed.of(seed & 0xffffffffL)), work);
                leaves.put(seed, l);
            }
            return l;
        }
    }

    private final Context ctx;
    private final Config rootConfig;
    private final Seed baseSeed;
    private final Leaf baseLeaf;
    private final double leafDomainEnd;
    private final Chain<Entry> entries;
    private final int n;
    private final Chain<Layer> layers;
    private final int m;
    private final double domainEnd;
    private final double from;
    private final Leaf lastLeaf;
    private final long folded;
    private final String witness;

    private Timeline(Context ctx, Config rootConfig, Seed baseSeed, Leaf baseLeaf, Chain<Entry> entries, int n, Chain<Layer> layers, int m,
                      double domainEnd, double from, Leaf lastLeaf, long folded, String witness) {
        this.ctx = ctx;
        this.rootConfig = rootConfig;
        this.baseSeed = baseSeed;
        this.baseLeaf = baseLeaf;
        this.leafDomainEnd = baseLeaf.domainEnd();
        this.entries = entries;
        this.n = n;
        this.layers = layers;
        this.m = m;
        this.domainEnd = domainEnd;
        this.from = from;
        this.lastLeaf = lastLeaf;
        this.folded = folded;
        this.witness = witness;
    }

    /** {@code create}: a root timeline of one leaf. */
    public static Timeline create(Config config) { return create(config,false); }
    public static Timeline createCoherent(Config config) { return create(config,true); }
    private static Timeline create(Config config,boolean coherent) {
        Context ctx = new Context(config,coherent);
        Leaf leaf = Leaf.create(config, ctx.work);
        return new Timeline(ctx, config, config.seed(), leaf, new Chain<>(), 0, new Chain<>(), 0, leaf.domainEnd(), 1, leaf, 0, "");
    }

    /**
     * A timeline resumed from a checkpoint: its base leaf is the one with {@code leafSeed}, nothing before {@code from} can be sampled,
     * and {@code folded} entries are already behind it under {@code witness}. {@code rootConfig} is the root's, which the lineage began with.
     */
    static Timeline resume(Config rootConfig, Seed leafSeed, double from, long folded, String witness) { return resume(rootConfig,leafSeed,from,folded,witness,false); }
    static Timeline resume(Config rootConfig,Seed leafSeed,double from,long folded,String witness,boolean coherent) {
        Context ctx = new Context(rootConfig,coherent);
        Leaf leaf = Leaf.create(rootConfig.withSeed(leafSeed), ctx.work);
        Leaf.coordinate(from);
        return new Timeline(ctx, rootConfig, leafSeed, leaf, new Chain<>(), 0, new Chain<>(), 0, leaf.domainEnd(), from, leaf, folded, witness);
    }

    public Config rootConfig() {
        return rootConfig;
    }

    /** The configuration of the present leaf (the seed of the last future). */
    public Config leafConfig() {
        return lastLeaf.config();
    }

    /** The number of forks in the lineage held (after a tail restore, those still open). */
    public int forks() {
        return n;
    }

    Timeline withoutInfluences() {
        return new Timeline(ctx,rootConfig,baseSeed,baseLeaf,entries,n,new Chain<>(),0,leafDomainEnd,from,lastLeaf,folded,witness);
    }

    public int influences() {
        return m;
    }

    /** The effective upper end of the domain: unreleased influences with finite guides shorten it, released ones do not. */
    public double domainEnd() {
        return domainEnd;
    }

    /** The first coordinate this timeline can be sampled at (1 for a full lineage, a checkpoint's for a tail). */
    public boolean checkpointed(){return from>1||folded>0||!witness.isEmpty();}

    public double from() {
        return from;
    }

    /** Ridge-field evaluations performed through this lineage so far: the work, counted. */
    long influenceReads(){return ctx.influenceReads.get();}

    public long work() {
        return ctx.work.get();
    }

    public Entry entry(int i) {
        if (i < 0 || i >= n) {
            throw new IndexOutOfBoundsException();
        }
        return entries.get(i);
    }

    public Influence influenceAt(int i) {
        if (i < 0 || i >= m) {
            throw new IndexOutOfBoundsException();
        }
        return layers.get(i).influence();
    }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(entries.get(i));
        }
        return out;
    }

    public List<Influence> influenceList() {
        List<Influence> out = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            out.add(layers.get(i).influence());
        }
        return out;
    }

    /** The seed of the leaf the next fork would leave. */
    Seed tipSeed() {
        return n > 0 ? Seed.of(entries.get(n - 1).seed() & 0xffffffffL) : baseSeed;
    }

    Seed baseSeed() {
        return baseSeed;
    }

    long folded() {
        return folded;
    }

    String witness() {
        return witness;
    }

    private Leaf leafOf(int i) {
        return i == n - 1 ? lastLeaf : ctx.leaf(entries.get(i).seed());
    }

    double latestWriteTime(){return latest();}

    private double latest() {
        return Math.max(latestEvent(), from);
    }

    private double latestEvent() {
        double latest = 1;
        if (n > 0) {
            latest = Math.max(latest, entries.get(n - 1).at());
        }
        if (m > 0) {
            latest = Math.max(latest, layers.get(m - 1).latestWrite());
        }
        return latest;
    }

    // ----- sampling ---------------------------------------------------------------------------------------------------------------

    /** The sample at {@code x}. */
    public Sample sample(double x) {
        return evaluate(x, null);
    }

    /** The sample at {@code x} with the latent position forced to {@code h} (vixen.mjs {@code at}). */
    public Sample at(double x, double[] h) {
        return evaluate(x, Config.vector(h, 1000000));
    }

    /** {@code n} evenly spaced samples from {@code x0} to {@code x1} inclusive (work item V7's batch form). */
    public Sample[] sampleRange(double x0, double x1, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("A range holds at least one sample");
        }
        Sample[] out = new Sample[count];
        for (int i = 0; i < count; i++) {
            double x = count == 1 ? x0 : i == count - 1 ? x1 : x0 + (x1 - x0) * i / (count - 1);
            out[i] = sample(x);
        }
        return out;
    }

    private Sample evaluate(double x, double[] h) {
        Leaf.coordinate(x);
        if (x > domainEnd) {
            throw new IllegalArgumentException("x exceeds the effective domain");
        }
        if (x < from) {
            throw new IllegalArgumentException("x lies before this timeline's checkpoint: the past is the spun record's");
        }
        Sample s = lineageSample(x, h);
        if (m == 0) {
            return s;
        }
        Layer latest=readLayer(m-1);
        if(x>=latest.prefixEnd()||x<=latest.prefixStart())return s;
        int index=m-1;
        List<Influence> active=new ArrayList<>();
        while(index>=0){
            Layer layer=readLayer(index);Influence influence=layer.influence();
            if(influence.amount()==0||x<=influence.at()||x>=influence.end()){
                boolean skipped=false;
                for(int level=layer.jumps().size()-1;level>=0;level--){
                    ctx.influenceReads.incrementAndGet();LayerJump jump=layer.jumps().get(level);
                    if(x<=jump.minStart()||x>=jump.maxEnd()){index=jump.ancestor();skipped=true;break;}
                }
                if(!skipped)index--;
            }else{active.add(influence);index--;}
        }
        for(int i=active.size()-1;i>=0;i--)s=active.get(i).apply(s,x,rootConfig);
        return s;
    }

    private Layer readLayer(int index){ctx.influenceReads.incrementAndGet();return layers.get(index);}

    private Sample sampleLeaf(Leaf leaf, double x, double[] h) {
        return h == null ? leaf.sample(x) : leaf.at(x, h);
    }

    private Sample lineageSample(double x, double[] h) {
        // k: the last fork attached before x (forks at or after x are the parent's future); entries are sorted by coordinate
        int lo = 0;
        int hi = n - 1;
        int k = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (entries.get(mid).at() < x) {
                k = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        int j = k;
        while (j >= 0 && x < entries.get(j).end()) {
            j--;
        }
        Sample s = j >= 0 ? sampleLeaf(leafOf(j), x, h) : sampleLeaf(baseLeaf, x, h);
        for (int i = j + 1; i <= k; i++) {
            Entry e = entries.get(i);
            double w = Opening.smooth5((x - e.at()) / e.handoffWidth());
            s = Sample.mix(s, sampleLeaf(leafOf(i), x, h), w,ctx.coherent);
        }
        return s;
    }

    Analytic.Result analyticJet(double x,double[] h){
        Leaf.coordinate(x);
        if(x<from||x>domainEnd)throw new IllegalArgumentException("x outside the available analytic domain");
        if(h!=null)h=Config.vector(h,1_000_000);
        int lo=0,hi=n-1,k=-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;if(entries.get(mid).at()<x){k=mid;lo=mid+1;}else hi=mid-1;}
        int j=k;while(j>=0&&x<entries.get(j).end())j--;
        Leaf initial=j>=0?leafOf(j):baseLeaf;
        Analytic.Result result=h==null?Analytic.leaf(initial,x):Analytic.leaf(initial,x,h);
        for(int i=j+1;i<=k;i++){
            Entry e=entries.get(i);Leaf child=leafOf(i);
            Analytic.Result next=h==null?Analytic.leaf(child,x):Analytic.leaf(child,x,h);
            Operations.Jet weight=handoffJet(x,e.at(),e.handoffWidth());
            Sample primal=Sample.mix(result.sample(),next.sample(),weight.value(),ctx.coherent);
            result=CompositionAnalytic.blend(result,next,weight,primal,ctx.coherent);
        }
        if(m==0)return result;
        Layer latest=readLayer(m-1);
        if(x>=latest.prefixEnd()||x<=latest.prefixStart())return result;
        List<Influence> active=new ArrayList<>();int index=m-1;
        while(index>=0){
            Layer layer=readLayer(index);Influence effect=layer.influence();
            if(effect.amount()==0||x<=effect.at()||x>=effect.end()){
                boolean skipped=false;
                for(int level=layer.jumps().size()-1;level>=0;level--){
                    LayerJump block=layer.jumps().get(level);ctx.influenceReads.incrementAndGet();
                    if(x<=block.minStart()||x>=block.maxEnd()){index=block.ancestor();skipped=true;break;}
                }
                if(!skipped)index--;
            }else{active.add(effect);index--;}
        }
        for(int i=active.size()-1;i>=0;i--){
            Influence effect=active.get(i);Sample primal=effect.apply(result.sample(),x,rootConfig);
            result=CompositionAnalytic.influence(result,effect,x,rootConfig,primal);
        }
        return result;
    }

    static Operations.Jet handoffJet(double x,double at,double width){
        double t=(x-at)/width;
        if(t<=0)return new Operations.Jet(0,0,0);
        if(t>=1)return new Operations.Jet(1,0,0);
        double first=30*t*t*(t-1)*(t-1)/width;
        double second=60*t*(t-1)*(2*t-1)/width/width;
        return new Operations.Jet(Opening.smooth5(t),first,second);
    }

    // ----- forks and influences ----------------------------------------------------------------------------------------------------

    /** A fork at {@code at} with the default handoff {@code min(16, domain end - at)}. */
    public Timeline fork(double at, String operation) {
        return fork(at, operation, null);
    }

    /** {@code fork}: a new future from {@code at}, joined over {@code handoffWidth}. A fork never precedes an earlier event. */
    public Timeline fork(double at, String operation, Double handoffWidth) {
        return forkChecked(at, operation, handoffWidth, true);
    }

    private Timeline forkChecked(double at, String operation, Double handoffWidth, boolean live) {
        Leaf.coordinate(at);
        if (at > leafDomainEnd) {
            throw new IllegalArgumentException("x exceeds the canonical clock domain");
        }
        if (operation == null || operation.isEmpty()) {
            throw new IllegalArgumentException("A nonempty operation string is required");
        }
        Hash.seed32(operation);
        if (at < (live ? latest() : latestEvent())) {
            throw new IllegalArgumentException("Nested forks cannot precede their parent fork");
        }
        double width = handoffWidth == null ? Math.min(16, domainEnd - at) : handoffWidth;
        if (Double.isNaN(width) || Double.isInfinite(width) || width <= 0 || at + width <= at || at + width > domainEnd) {
            throw new IllegalArgumentException("Handoff must have positive representable width inside the domain");
        }
        int seed = Hash.forkSeed(tipSeed(), operation, at);
        Entry entry = new Entry(at, operation, width, seed);
        Leaf future = Leaf.create(rootConfig.withSeed(Seed.of(seed & 0xffffffffL)), ctx.work);
        Chain<Entry> next = entries.append(n, entry);
        return new Timeline(ctx, rootConfig, baseSeed, baseLeaf, next, n + 1, layers, m, domainEnd, from, future, folded, witness);
    }

    /** Re-attaches a recorded fork as a replay does: no check against the checkpoint, since a handoff still open began before it. */
    Timeline replayFork(double at, String operation, Double handoffWidth) {
        return forkChecked(at, operation, handoffWidth, false);
    }

    /** {@code influence}: pulls the timeline toward a guide from {@code at}. An influence never precedes an earlier event. */
    public Timeline influence(Influence influence) {
        return withLayer(influence, false);
    }

    private Timeline withLayer(Influence influence, boolean carried) {
        double end = influence.validate(1, domainEnd);
        if (!carried && influence.at() < latest()) {
            throw new IllegalArgumentException("Influence cannot precede existing timeline events");
        }
        if (!carried && influence.at() < from) {
            throw new IllegalArgumentException("An influence cannot begin before the checkpoint");
        }
        double start=influence.amount()==0?Double.POSITIVE_INFINITY:influence.at();
        double finish=influence.amount()==0?Double.NEGATIVE_INFINITY:influence.end();
        double prefixEnd=m>0?Math.max(layers.get(m-1).prefixEnd(),finish):finish;
        double prefixStart=m>0?Math.min(layers.get(m-1).prefixStart(),start):start;
        List<LayerJump> jumps=new ArrayList<>();jumps.add(new LayerJump(m-1,start,finish));
        for(int level=1;;level++){
            LayerJump previous=jumps.get(level-1);if(previous.ancestor()<0)break;
            Layer older=layers.get(previous.ancestor());if(older.jumps().size()<level)break;
            LayerJump other=older.jumps().get(level-1);
            jumps.add(new LayerJump(other.ancestor(),Math.min(previous.minStart(),other.minStart()),Math.max(previous.maxEnd(),other.maxEnd())));
        }
        Chain<Layer> next=layers.append(m,new Layer(influence,prefixEnd,prefixStart,m>0?Math.max(layers.get(m-1).latestWrite(),influence.at()):influence.at(),List.copyOf(jumps)));
        return new Timeline(ctx, rootConfig, baseSeed, baseLeaf, entries, n, next, m + 1, Math.min(domainEnd, end), from, lastLeaf, folded, witness);
    }

    /** Re-attaches an influence as a replay does: in order, without the check against later events. */
    Timeline replayInfluence(Influence influence) {
        return withLayer(influence, true);
    }

    // ----- tail and snapshot ------------------------------------------------------------------------------------------------------

    /**
     * {@code tail(from)} (work item V2): folds every fork whose handoff has closed by {@code from} (and every influence released by then) into
     * a checkpoint. A timeline restored from it samples bitwise as this one for every {@code x >= from}.
     */
    public Tail tail(double from) {
        Leaf.coordinate(from);
        if (from < this.from) {
            throw new IllegalArgumentException("A tail cannot reach back before the timeline's own checkpoint");
        }
        int c = -1;
        for (int i = n - 1; i >= 0; i--) {
            if (entries.get(i).end() <= from) {
                c = i;
                break;
            }
        }
        List<Entry> foldedEntries = new ArrayList<>();
        for (int i = 0; i <= c; i++) {
            foldedEntries.add(entries.get(i));
        }
        List<Entry> open = new ArrayList<>();
        for (int i = c + 1; i < n; i++) {
            open.add(entries.get(i));
        }
        List<Influence> foldedInfluences = new ArrayList<>();
        List<Influence> openInfluences = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            Influence inf = layers.get(i).influence();
            if (inf.end() <= from) {
                foldedInfluences.add(inf);
            } else {
                openInfluences.add(inf);
            }
        }
        Seed leafSeed = c >= 0 ? Seed.of(entries.get(c).seed() & 0xffffffffL) : baseSeed;
        String chain = ctx.coherent?Tail.historyChain(witness,foldedEntries):Tail.chain(witness, foldedEntries, foldedInfluences);
        return new Tail(rootConfig, leafSeed, from, folded + foldedEntries.size() + (ctx.coherent?0:foldedInfluences.size()), chain, open, openInfluences,ctx.coherent);
    }

    /** The timeline's whole lineage as Vixen's snapshot value (a tail-restored timeline snapshots only what it still holds). */
    public Snapshot snapshot() {
        List<Snapshot.Fork> forks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            forks.add(new Snapshot.Fork(e.at(), e.operation(), e.handoffWidth()));
        }
        return new Snapshot(rootConfig, forks, influenceList(),ctx.coherent,checkpointed()?new Snapshot.Checkpoint(baseSeed,from,folded,witness):null);
    }

    /** {@code restore} of a snapshot of the current algorithm: the root, every fork in order, then every influence. */
    public static Timeline restore(Snapshot snapshot) {
        Snapshot.Checkpoint checkpoint=snapshot.checkpoint();
        Timeline t = checkpoint==null?create(snapshot.config(),snapshot.coherent()):resume(snapshot.config(),checkpoint.seed(),checkpoint.from(),checkpoint.folded(),checkpoint.witness(),snapshot.coherent());
        for (Snapshot.Fork f : snapshot.forks()) {
            t = checkpoint==null?t.fork(f.at(), f.operation(), f.handoffWidth()):t.replayFork(f.at(), f.operation(), f.handoffWidth());
        }
        for (Influence inf : snapshot.influences()) {
            t = t.replayInfluence(inf);
        }
        return t;
    }

    /** Vixen's replay value: the root configuration, the forks, then the influences. */
    public record Snapshot(Config config, List<Fork> forks, List<Influence> influences,boolean coherent,Checkpoint checkpoint) {
        public Snapshot(Config config,List<Fork> forks,List<Influence> influences){this(config,forks,influences,false,null);}
        public Snapshot(Config config,List<Fork> forks,List<Influence> influences,boolean coherent){this(config,forks,influences,coherent,null);}
        public record Checkpoint(Seed seed,double from,long folded,String witness){
            public Checkpoint{if(seed==null||!Double.isFinite(from)||from<1||folded<0||folded>9007199254740991L||witness==null)throw new IllegalArgumentException("Invalid checkpoint state");}
        }
        public Snapshot {
            if(config==null||checkpoint!=null&&(checkpoint.from()>Leaf.create(config).domainEnd()||coherent&&!checkpoint.witness().matches("[0-9a-f]{64}")))throw new IllegalArgumentException("Invalid checkpoint snapshot");
            forks = List.copyOf(forks);
            influences = List.copyOf(influences);
        }

        /** A recorded fork. */
        public record Fork(double at, String operation, double handoffWidth) {
        }
    }
}
