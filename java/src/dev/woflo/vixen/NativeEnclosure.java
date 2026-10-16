package dev.woflo.vixen;

import java.util.*;

/** Outward interval oracle for native fork timelines without influences or transformed operations. */
public final class NativeEnclosure implements Crossing.EnclosureProvider {
    public record Range(double lo, double hi) {
        public Range { if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi) throw new IllegalArgumentException("Invalid finite interval"); }
        public double[] array() { return new double[] {lo, hi}; }
    }
    private record Fork(double at, double width, Seed seed) { double end() { return at + width; } }
    private record State(R base, R unconstrained, R guide, R floor, R ceiling, R width, R applied) {}
    private record LeafData(Config config, int seed, Ridges.Ridge[] ridges, Opening.Dip[] dips) {}
    private record R(double lo, double hi) {
        R { if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi) throw new ArithmeticException("Unresolved: no finite interval"); }
    }

    private final Curve source;
    private final Config config;
    private final boolean coherent;
    private final double from, domainEnd;
    private final List<Fork> forks;
    private final List<LeafData> leaves;
    private final double[] coordinates;

    public static NativeEnclosure forCurve(Curve curve) { return new NativeEnclosure(curve, null); }
    public static NativeEnclosure forCurve(Curve curve, double[] coordinates) { return new NativeEnclosure(curve, coordinates); }

    private NativeEnclosure(Curve curve, double[] coordinates) {
        if (!(curve instanceof Vixen.TimelineCurve nativeCurve))
            throw new UnsupportedOperationException("Native enclosures refuse transformed curves");
        Timeline.Snapshot snapshot = nativeCurve.timeline().snapshot(); // One snapshot fixes the source lineage and checkpoint metadata.
        if (!snapshot.influences().isEmpty()) throw new UnsupportedOperationException("Native influences are not enclosed");
        this.source = curve;
        this.config = snapshot.config();
        this.coherent = snapshot.coherent();
        this.domainEnd = curve.domainEnd();
        this.from = snapshot.checkpoint() == null ? 1 : snapshot.checkpoint().from();
        this.coordinates = coordinates == null ? null : Config.vector(coordinates, 1_000_000);
        Seed parent = snapshot.checkpoint() == null ? config.seed() : snapshot.checkpoint().seed();
        List<Fork> fs = new ArrayList<>();
        List<LeafData> ls = new ArrayList<>();
        ls.add(leaf(parent));
        for (Timeline.Snapshot.Fork f : snapshot.forks()) {
            int next = Hash.forkSeed(parent, f.operation(), f.at());
            parent = Seed.of(next & 0xffffffffL);
            fs.add(new Fork(f.at(), f.handoffWidth(), parent));
            ls.add(leaf(parent));
        }
        this.forks = List.copyOf(fs);
        this.leaves = List.copyOf(ls);
    }
    private LeafData leaf(Seed seed) {
        Config c = config.withSeed(seed);
        int id = seed.u32();
        return new LeafData(c, id, Ridges.of(c, id), Opening.dips(id, c.introVariation()));
    }
    @Override public Object source() { return source; }
    public double availableFrom() { return from; }
    public double domainEnd() { return domainEnd; }
    public boolean coherent() { return coherent; }
    @Override public double[] enclose(double lo, double hi, String measure) {
        if (!"deviation".equals(measure)) throw new UnsupportedOperationException("Native oracle supports deviation only");
        return encloseDeviation(lo, hi);
    }

    /** Encloses every binary64 sample coordinate in [lo,hi], including branch boundaries. */
    public double[] encloseDeviation(double lo, double hi) {
        if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi) throw new IllegalArgumentException("Finite ordered bounds required");
        if (lo < Math.max(1, from) || hi > domainEnd) throw new IllegalArgumentException("Interval outside available native domain");
        List<Double> cuts = new ArrayList<>(); cuts.add(lo); cuts.add(hi);
        addCut(cuts, config.openingEndpoint(), lo, hi);
        for (Fork f : forks) { addCut(cuts, f.at(), lo, hi); addCut(cuts, f.end(), lo, hi); }
        cuts.sort(Double::compare);
        List<Double> unique = new ArrayList<>();
        for (double x : cuts) if (unique.isEmpty() || Double.compare(x, unique.get(unique.size()-1)) != 0) unique.add(x);
        R result = null;
        for (double x : unique) result = hull(result, point(x));
        for (int i = 0; i + 1 < unique.size(); i++) {
            double a = Math.nextUp(unique.get(i)), b = Math.nextDown(unique.get(i+1));
            if (a <= b) result = hull(result, timeline(new R(a, b), a / 2 + b / 2));
        }
        if (result == null) throw new ArithmeticException("Unresolved: empty interval");
        return new double[] {result.lo(), result.hi()};
    }
    private static void addCut(List<Double> cuts, double x, double lo, double hi) { if (x >= lo && x <= hi) cuts.add(x); }
    private R point(double x) {
        Sample s = coordinates == null ? source.sample(x) : source.at(x, coordinates);
        if (!Double.isFinite(s.deviation())) throw new ArithmeticException("Unresolved: nonfinite sample");
        return r(s.deviation());
    }
    private R timeline(R x, double representative) {
        int k = -1;
        for (int i = 0; i < forks.size(); i++) if (forks.get(i).at() < representative) k = i;
        int j = k;
        while (j >= 0 && representative < forks.get(j).end()) j--;
        State state = state(j < 0 ? 0 : j, x);
        for (int i = j + 1; i <= k; i++) {
            Fork f = forks.get(i);
            R w = smooth5(div(sub(x, r(f.at())), r(f.width())));
            state = mix(state, state(i + 1, x), w);
        }
        return div(sub(state.applied(), state.base()), state.base());
    }
    private State state(int leafIndex, R x) { return raw(leaves.get(leafIndex), x); }
    private State raw(LeafData d, R x) {
        Config c = d.config();
        R score = r(0), guideScore = r(0);
        if (x.hi() > c.openingEndpoint()) {
            if (x.lo() <= c.openingEndpoint()) throw new IllegalStateException("Opening branch must be partitioned");
            R clock = clock(c, x), u = sub(clock, r(100));
            for (Ridges.Ridge q : d.ridges()) {
                R p, g;
                if (coordinates == null) {
                    p = add(r(q.phase()), mul(u, r(q.slope())));
                    g = add(r(q.guidePhase()), mul(u, r(q.guideSlope())));
                } else {
                    R projection = r(0);
                    double[] direction = q.direction();
                    for (int z = 0; z < 16; z++) projection = add(projection, mul(r(direction[z]), r(coordinates[z])));
                    p = add(add(r(q.basePhase()), projection), div(u, r(q.scale())));
                    g = add(add(r(q.basePhase()), div(projection, r(32))), div(u, r(32 * q.scale())));
                }
                score = add(score, div(noise(p, d.seed(), q.channel()), r(48)));
                guideScore = add(guideScore, div(noise(g, d.seed(), q.channel()), r(48)));
            }
        }
        R gate = smooth5(div(sub(x, r(c.openingEndpoint())), r(c.ramp())));
        R raw = mul(mul(mul(r(c.strength()), gate), r(2.2)), tanh(mul(r(c.contrast()), score)));
        R guideDeviation = mul(mul(mul(r(c.strength()), gate), r(.25)), tanh(mul(r(2), guideScore)));
        double w = Opening.smooth5(c.stabilizer());
        R deviation = c.stabilizer() == 0 ? raw : c.stabilizer() == 1 ? guideDeviation : add(raw, mul(sub(guideDeviation, raw), r(w)));
        R base = authored(c, d.dips(), x);
        R unconstrained = add(base, mul(base, deviation));
        R unconstrainedGuide = add(base, mul(base, guideDeviation));
        R floor = c.floor() == Config.Floor.NONE ? null : c.floor() == Config.Floor.ZERO ? r(0)
                : c.floor() == Config.Floor.BASELINE ? base : unconstrainedGuide;
        R ceiling = c.ceilingMargin() == null ? null : add(c.floor() == Config.Floor.BASELINE ? base : unconstrainedGuide,
                mul(r(c.ceilingMargin()), base));
        R amplitude = mul(mul(r(c.strength()), gate), r(c.boundarySoftness()));
        R width = width(floor, ceiling, amplitude, base);
        R applied = apply(unconstrained, floor, ceiling, width);
        return new State(base, unconstrained, unconstrainedGuide, floor, ceiling, width, applied);
    }

    private static State mix(State a, State b, R w) {
        R base = blend(a.base(), b.base(), w), uv = blend(a.unconstrained(), b.unconstrained(), w);
        R ug = blend(a.guide(), b.guide(), w), width = blend(a.width(), b.width(), w);
        R floor = blendNullable(a.floor(), b.floor(), w), ceiling = blendNullable(a.ceiling(), b.ceiling(), w);
        if (a.floor() == null != (b.floor() == null) || a.ceiling() == null != (b.ceiling() == null))
            throw new UnsupportedOperationException("Incompatible native constraint states");
        R applied;
        if (a.applied() == null || b.applied() == null) applied = apply(uv, floor, ceiling, width);
        else if (NativeEnclosureHolder.coherent) applied = apply(uv, floor, ceiling, width);
        else {
            R value = blend(a.applied(), b.applied(), w);
            if (floor != null) value = max(value, floor);
            if (ceiling != null) value = min(value, ceiling);
            applied = value;
        }
        return new State(base, uv, ug, floor, ceiling, width, applied);
    }
    /* Set per mix call without global mutable state. */
    private static final class NativeEnclosureHolder { static boolean coherent; }
    private static R blendValue(R a, R b, R w) { return add(a, mul(sub(b, a), w)); }
    private static R blendNullable(R a, R b, R w) { return a == null && b == null ? null : a == null || b == null ? null : blendValue(a,b,w); }

    private static R width(R lo, R hi, R a, R base) {
        if (lo == null && hi == null) return r(0);
        if (lo == null || hi == null) return mul(base, a);
        R gap = sub(hi, lo);
        if (gap.hi() <= 0 || a.hi() == 0) return r(0);
        double g0 = Math.max(0, gap.lo()), g1 = Math.max(0, gap.hi());
        double low = gap.lo() > 0 && a.lo() > 0 ? fraction(g0, a.lo()).lo() : 0;
        double high = fraction(g1, a.hi()).hi();
        return mul(base, new R(Math.max(0, low), Math.max(0, high)));
    }
    private static R fraction(double gap, double a) {
        if (gap == 0 || a == 0) return r(0);
        return div(mul(r(a), r(gap)), add(r(gap), mul(r(2), r(a))));
    }
    private static R apply(R y, R lo, R hi, R e) {
        if (lo != null && hi != null && hi.hi() <= lo.lo()) return lo;
        R out = lo == null ? y : floorMap(y, lo, e);
        if (hi != null) out = ceilMap(out, hi, e);
        if (lo != null && hi != null && hi.lo() <= lo.hi()) out = hull(out, lo);
        return out;
    }
    private static R floorMap(R y, R lo, R e) {
        if (e.lo() < 0) throw new ArithmeticException("Negative boundary width");
        R out = null;
        if (e.lo() == 0) out = new R(Math.max(y.lo(), lo.lo()), Math.max(y.hi(), lo.hi()));
        if (y.lo() <= lo.hi()) out = hull(out, lo);
        if (y.hi() >= add(lo, e).lo()) out = hull(out, y);
        if (e.hi() > 0 && y.hi() > lo.lo()) {
            R ratio = e.lo() == 0 ? new R(0, 1) : div(sub(y, lo), e);
            double a = Math.max(0, ratio.lo()), b = Math.min(1, ratio.hi());
            if (a <= b) out = hull(out, add(lo, mul(e, rampRange(new R(a, b)))));
        }
        if (out == null) throw new ArithmeticException("Unresolved boundary branches");
        return out;
    }
    private static R ceilMap(R y, R hi, R e) { R q = floorMap(new R(-y.hi(), -y.lo()), new R(-hi.hi(), -hi.lo()), e); return new R(-q.hi(), -q.lo()); }
    private static R rampRange(R t) { return mul(mul(mul(t,t),t), add(r(6), mul(t, add(r(-8), mul(r(3),t))))); }
    private static R max(R a, R b) { return new R(Math.max(a.lo(),b.lo()), Math.max(a.hi(),b.hi())); }
    private static R min(R a, R b) { return new R(Math.min(a.lo(),b.lo()), Math.min(a.hi(),b.hi())); }

    private static R clock(Config c, R x) { return c.openingEndpoint() == 100 ? x : add(r(1), div(mul(sub(x,r(1)),r(99)),r(c.openingEndpoint()-1))); }
    private static R authored(Config c, Opening.Dip[] dips, R x) {
        R t = clock(c, x), a = sub(t,r(42)), b = sub(t,r(71));
        R first=mul(r(25),exp(mul(r(-1),div(mul(a,a),r(32)))));
        R second=mul(r(38),exp(mul(r(-1),div(mul(b,b),r(28)))));
        R old=sub(sub(mul(t,exp(div(t,r(100)))),first),second);
        if (c.introVariation() == 0 || t.lo() >= 100) return old;
        if (t.hi() > 100) throw new IllegalStateException("Authored opening branch must be partitioned");
        R sum = r(0);
        for (Opening.Dip d : dips) {
            R z = div(sub(t,r(d.center())),r(d.width()));
            sum = add(sum,mul(r(d.depth()),exp(mul(r(-1),mul(z,z)))));
        }
        R fresh = sub(mul(t,exp(div(t,r(100)))),sum);
        R weight = sub(r(1),smooth5(div(sub(t,r(90)),r(10))));
        return add(old,mul(sub(fresh,old),weight));
    }
    private static R noise(R p, int seed, int channel) {
        if (Math.floor(p.hi()) - Math.floor(p.lo()) > 1) return new R(-1-2048*Math.ulp(1.0),1+2048*Math.ulp(1.0));
        R out = null;
        int first = (int)Math.floor(p.lo()), last = (int)Math.floor(p.hi());
        for (int k=first;k<=last;k++) {
            double aa=Math.max(p.lo(),k), bb=Math.min(p.hi(),k+1.0);
            if (aa>bb) continue;
            R t=new R(aa-k,bb-k); double a=2*Hash.unit(seed,k,channel)-1, b=2*Hash.unit(seed,k+1,channel)-1;
            R q=add(r(a),mul(sub(r(b),r(a)),smooth5(t)));
            out=hull(out,q);
        }
        return out==null?new R(-1-2048*Math.ulp(1.0),1+2048*Math.ulp(1.0)):out;
    }
    private static final double POLYNOMIAL_ROUND_ERROR=512*Math.ulp(1.0);
    private static R smooth5(R x){
        if(x.hi()<=0)return r(0);
        if(x.lo()>=1)return r(1);
        if(x.lo()==x.hi())return smoothPoint(x.lo());
        return new R(Math.max(0,down(smoothPoint(x.lo()).lo()-POLYNOMIAL_ROUND_ERROR)),up(smoothPoint(x.hi()).hi()+POLYNOMIAL_ROUND_ERROR));
    }
    private static R smoothPoint(double x){
        if(x<=0)return r(0);
        if(x>=1)return r(1);
        R t=r(x),t3=mul(mul(t,t),t);
        R value=mul(t3,add(r(10),mul(t,add(r(-15),mul(r(6),t)))));
        return new R(Math.max(0,value.lo()),value.hi());
    }

    private static R exp(R x) { return new R(Math.max(0,expPoint(x.lo()).lo()),expPoint(x.hi()).hi()); }
    private static R expPoint(double x) {
        double v=StrictMath.exp(x); if(!Double.isFinite(v))throw new ArithmeticException("Unresolved: exponential overflow");
        double lo=v,hi=v; for(int i=0;i<2;i++){lo=Math.nextDown(lo);hi=Math.nextUp(hi);}
        return new R(Math.max(0,lo),hi);
    }
    private static R tanh(R x) {
        R result=new R(tanhPoint(x.lo()).lo(),tanhPoint(x.hi()).hi());
        return new R(Math.max(-1,result.lo()),Math.min(1,result.hi()));
    }
    private static R tanhPoint(double x) {
        R ax=r(Math.abs(x)), exponent=mul(r(-2),ax), q=exp(exponent);
        R one=r(1), val=div(sub(one,q),add(one,q));
        R result=x<0?new R(-val.hi(),-val.lo()):new R(Math.max(0,val.lo()),Math.min(1,val.hi()));
        // StrictMath.tanh is within a few adjacent binary64 values; widen locally by ulps, not by a fixed epsilon.
        double actual=StrictMath.tanh(x), lo=actual, hi=actual;
        for(int i=0;i<4;i++){lo=Math.nextDown(lo);hi=Math.nextUp(hi);}
        return hull(result,new R(Math.max(-1,lo),Math.min(1,hi)));
    }

    private static R r(double x) { return new R(x,x); }
    private static R hull(R a,R b) { return a==null?b:b==null?a:new R(Math.min(a.lo(),b.lo()),Math.max(a.hi(),b.hi())); }
    private static double down(double x) { if(!Double.isFinite(x))throw new ArithmeticException("Unresolved: arithmetic overflow");double y=Math.nextDown(x);if(!Double.isFinite(y))throw new ArithmeticException("Unresolved: no finite lower enclosure");return y; }
    private static double up(double x) { if(!Double.isFinite(x))throw new ArithmeticException("Unresolved: arithmetic overflow");double y=Math.nextUp(x);if(!Double.isFinite(y))throw new ArithmeticException("Unresolved: no finite upper enclosure");return y; }
    private static R add(R a,R b) {
        if(a.lo()==0&&a.hi()==0)return b;if(b.lo()==0&&b.hi()==0)return a;
        double lo=down(a.lo()+b.lo()),hi=up(a.hi()+b.hi());
        if(a.lo()>=0&&b.lo()>=0)lo=Math.max(0,lo);if(a.hi()<=0&&b.hi()<=0)hi=Math.min(0,hi);return new R(lo,hi);
    }
    private static R sub(R a,R b) {
        if(b.lo()==0&&b.hi()==0)return a;
        double lo=down(a.lo()-b.hi()),hi=up(a.hi()-b.lo());
        if(a.lo()>=b.hi())lo=Math.max(0,lo);if(a.hi()<=b.lo())hi=Math.min(0,hi);return new R(lo,hi);
    }
    private static R mul(R a,R b) {
        if(a.lo()==0&&a.hi()==0||b.lo()==0&&b.hi()==0)return r(0);
        if(a.lo()==1&&a.hi()==1)return b;if(b.lo()==1&&b.hi()==1)return a;
        if(a.lo()==-1&&a.hi()==-1)return new R(-b.hi(),-b.lo());if(b.lo()==-1&&b.hi()==-1)return new R(-a.hi(),-a.lo());
        double[] p={a.lo()*b.lo(),a.lo()*b.hi(),a.hi()*b.lo(),a.hi()*b.hi()};
        for(double v:p)if(!Double.isFinite(v))throw new ArithmeticException("Unresolved: multiplication overflow");
        double lo=down(Arrays.stream(p).min().orElseThrow()),hi=up(Arrays.stream(p).max().orElseThrow());
        if(a.lo()>=0&&b.lo()>=0||a.hi()<=0&&b.hi()<=0)lo=Math.max(0,lo);
        if(a.lo()>=0&&b.hi()<=0||a.hi()<=0&&b.lo()>=0)hi=Math.min(0,hi);return new R(lo,hi);
    }
    private static R div(R a,R b) {
        if(b.lo()<=0&&b.hi()>=0)throw new ArithmeticException("Unresolved: division interval contains zero");
        if(a.lo()==0&&a.hi()==0)return r(0);if(b.lo()==1&&b.hi()==1)return a;
        double[] p={a.lo()/b.lo(),a.lo()/b.hi(),a.hi()/b.lo(),a.hi()/b.hi()};
        for(double v:p)if(!Double.isFinite(v))throw new ArithmeticException("Unresolved: division overflow");
        double lo=down(Arrays.stream(p).min().orElseThrow()),hi=up(Arrays.stream(p).max().orElseThrow());
        if(a.lo()>=0&&b.lo()>0||a.hi()<=0&&b.hi()<0)lo=Math.max(0,lo);
        if(a.lo()>=0&&b.hi()<0||a.hi()<=0&&b.lo()>0)hi=Math.min(0,hi);return new R(lo,hi);
    }
    private static R blend(R a,R b,R w) { return add(a,mul(sub(b,a),w)); }
}
