package dev.woflo.vixen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** C2 impulses, conservative redistribution recipes, and gated ensemble coupling. */
public final class Operations {
    public static final String ALGORITHM = "vixen-operation-c2/1";
    private static final int MAX_COEFFICIENTS = 64, MAX_CURVES = 32;
    private static final double[] FACTOR = {0, 0, 0, 0, 1, -4, 6, -4, 1};
    private static final String CERT_KIND = "exact-real-endpoint-integral";
    private static final String CERT_POTENTIAL = "t^4*(1-t)^4*P(t)";
    private static final String CERT_PERTURBATION = "d/dt potential";
    private static final String CERT_INTEGRAL = "Q(1)-Q(0)=0";

    private Operations() {}

    /** A value and its first two coordinate derivatives. */
    public record Jet(double value, double first, double second) {}
    private sealed interface Recipe permits ImpulseRecipe, RedistributionRecipe { String type(); }
    private record ImpulseRecipe(double at, double magnitude, double relaxation, double attack) implements Recipe {
        @Override public String type() { return "impulse"; }
    }
    private record RedistributionRecipe(double from, double until, double[] coefficients, double magnitude, String measure) implements Recipe {
        RedistributionRecipe { coefficients = coefficients.clone(); }
        @Override public double[] coefficients() { return coefficients.clone(); }
        @Override public String type() { return "redistribution"; }
    }

    public static Curve withImpulse(Curve source, double at, double magnitude, double relaxation, Double attack) {
        if (source == null) throw new IllegalArgumentException("A Vixen curve is required");
        double a = attack == null ? relaxation / 8 : attack;
        ImpulseRecipe recipe = impulseRecipe(at, magnitude, relaxation, a);
        validateDomain(source, recipe, false);
        return new OperationCurve(source, recipe);
    }
    public static Curve withImpulse(Curve source, Map<String, ?> options) {
        Map<String, Object> m = fields(options, Set.of("at", "magnitude", "relaxation", "attack"), Set.of("at", "magnitude", "relaxation"), "impulse options");
        return withImpulse(source, finite(m.get("at"), "at"), finite(m.get("magnitude"), "magnitude"),
                finite(m.get("relaxation"), "relaxation"), m.containsKey("attack") ? finite(m.get("attack"), "attack") : null);
    }
    public static Curve withRedistribution(Curve source, double from, double until, double[] coefficients, double magnitude, String measure) {
        if (source == null) throw new IllegalArgumentException("A Vixen curve is required");
        RedistributionRecipe recipe = redistributionRecipe(from, until, coefficients, magnitude, measure);
        validateDomain(source, recipe, false);
        validateMeasure(source, recipe);
        return new OperationCurve(source, recipe);
    }

    /** Evaluate a serialized impulse or redistribution recipe without attaching it to a curve. */
    public static Jet perturbationJet(Map<String, ?> options, double x) {
        if (options == null) throw new IllegalArgumentException("Perturbation recipe is required");
        Object type = options.get("type");
        Recipe recipe;
        if ("impulse".equals(type)) {
            Map<String, Object> m = fields(options, Set.of("type", "at", "magnitude", "relaxation", "attack"),
                    Set.of("type", "at", "magnitude", "relaxation"), "impulse recipe");
            double relaxation = finite(m.get("relaxation"), "relaxation");
            double attack = m.containsKey("attack") ? finite(m.get("attack"), "attack") : relaxation / 8;
            recipe = impulseRecipe(finite(m.get("at"), "at"), finite(m.get("magnitude"), "magnitude"), relaxation, attack);
        } else if ("redistribution".equals(type)) recipe = parseRedistribution(options);
        else throw new IllegalArgumentException("Perturbation type must be impulse or redistribution");
        Jet result=jet(recipe,x);
        return checked(result.value(),result.first(),result.second());
    }

    private static ImpulseRecipe impulseRecipe(double at, double magnitude, double relaxation, double attack) {
        finite(at, "at"); finite(magnitude, "magnitude"); finite(relaxation, "relaxation"); finite(attack, "attack");
        if (relaxation <= 0 || attack <= 0) throw new IllegalArgumentException("Attack and relaxation must be positive");
        support(at, attack, relaxation);
        return new ImpulseRecipe(at, magnitude, relaxation, attack);
    }
    private static RedistributionRecipe redistributionRecipe(double from, double until, double[] coefficients, double magnitude, String measure) {
        finite(from, "window start"); finite(until, "window end"); finite(magnitude, "magnitude");
        width(from, until, "Redistribution window");
        if (coefficients == null || coefficients.length < 1 || coefficients.length > MAX_COEFFICIENTS)
            throw new IllegalArgumentException("Shape must contain 1 to 64 coefficients");
        double[] copy = coefficients.clone();
        for (int i = 0; i < copy.length; i++) finite(copy[i], "shape[" + i + "]");
        if (!("actualDeviation".equals(measure) || "unconstrainedDeviation".equals(measure)))
            throw new IllegalArgumentException("Invalid redistribution measure");
        double[] q = product(FACTOR, copy), d = derivative(q, 1);
        double radius = Math.abs(magnitude) * l1(d);
        finite(radius, "Redistribution bound");
        return new RedistributionRecipe(from, until, copy, magnitude, measure);
    }
    private static RedistributionRecipe parseRedistribution(Map<String, ?> input) {
        Map<String, Object> m = fields(input, Set.of("type", "from", "until", "coefficients", "magnitude", "measure", "certificate"),
                Set.of("type", "from", "until", "coefficients", "magnitude", "measure"), "redistribution recipe");
        if (!"redistribution".equals(m.get("type"))) throw new IllegalArgumentException("Invalid redistribution recipe");
        double[] coefficients = numbers(m.get("coefficients"), "coefficients");
        RedistributionRecipe r = redistributionRecipe(finite(m.get("from"), "window start"), finite(m.get("until"), "window end"),
                coefficients, finite(m.get("magnitude"), "magnitude"), text(m.get("measure"), "measure"));
        if (m.containsKey("certificate")) validateCertificate(m.get("certificate"));
        return r;
    }
    private static void validateCertificate(Object value) {
        Map<String, Object> c = fields(value, Set.of("kind", "potential", "perturbation", "integral"),
                Set.of("kind", "potential", "perturbation", "integral"), "algebra certificate descriptor");
        if (!CERT_KIND.equals(c.get("kind")) || !CERT_POTENTIAL.equals(c.get("potential"))
                || !CERT_PERTURBATION.equals(c.get("perturbation")) || !CERT_INTEGRAL.equals(c.get("integral")))
            throw new IllegalArgumentException("Invalid algebra certificate descriptor");
    }
    private static void validateMeasure(Curve source, RedistributionRecipe recipe) {
        if ("actualDeviation".equals(recipe.measure()) && recipe.magnitude() != 0 && !source.config().unconstrained())
            throw new IllegalArgumentException("Actual-deviation redistribution requires an unconstrained source; choose measure: unconstrainedDeviation explicitly");
    }
    private static void validateDomain(Curve source, Recipe recipe, boolean carried) {
        double lo = source.domainStart(), hi = source.domainEnd();
        if (recipe instanceof ImpulseRecipe r) {
            double rise = r.at() + r.attack(), end = rise + r.relaxation();
            if (r.at() < (carried ? 1 : lo) || rise > hi || end > hi || end <= lo)
                throw new IllegalArgumentException("Impulse support must lie inside the available domain");
        } else {
            RedistributionRecipe r = (RedistributionRecipe) recipe;
            if (r.from() < (carried ? 1 : lo) || r.until() > hi || (carried && r.until() <= lo))
                throw new IllegalArgumentException("Redistribution window must lie inside the available domain");
        }
    }
    private static double[] support(double at, double attack, double relaxation) {
        double rise = at + attack, end = rise + relaxation;
        if (!Double.isFinite(rise) || !Double.isFinite(end) || rise <= at || end <= rise)
            throw new IllegalArgumentException("Operation support must have positive representable widths");
        return new double[] {rise, end};
    }
    private static void width(double from, double until, String name) {
        if (until <= from || from + (until - from) <= from) throw new IllegalArgumentException(name + " must have positive representable width");
    }

    private static Jet jet(Recipe recipe, double x) {
        finite(x, "Jet coordinate");
        if (recipe instanceof ImpulseRecipe r) {
            if(r.magnitude()==0)return new Jet(0,0,0);
            double[] ends = support(r.at(), r.attack(), r.relaxation());
            if (x <= r.at() || x >= ends[1]) return new Jet(0, 0, 0);
            double t, v, d, dd, scale;
            if (x <= ends[0]) {
                t = (x - r.at()) / r.attack(); scale = r.attack();
                double[] j = smoothJet(t); v = j[0]; d = j[1]; dd = j[2];
                return new Jet(r.magnitude() * v, r.magnitude() * d / scale, r.magnitude() * dd / scale / scale);
            }
            t = (x - ends[0]) / r.relaxation(); scale = r.relaxation();
            double[] j = smoothJet(t); v = j[0]; d = j[1]; dd = j[2];
            return new Jet(r.magnitude() * (1 - v), -r.magnitude() * d / scale, -r.magnitude() * dd / scale / scale);
        }
        RedistributionRecipe r = (RedistributionRecipe) recipe;
        if (x <= r.from() || x >= r.until()) return new Jet(0, 0, 0);
        double width = r.until() - r.from(), t = (x - r.from()) / width;
        double[] f = polyJet(FACTOR, t), p = polyJet(r.coefficients, t);
        double value = r.magnitude() * (f[1] * p[0] + f[0] * p[1]);
        double first = r.magnitude() * (f[2] * p[0] + 2 * f[1] * p[1] + f[0] * p[2]) / width;
        double second = r.magnitude() * (f[3] * p[0] + 3 * f[2] * p[1] + 3 * f[1] * p[2] + f[0] * p[3]) / (width * width);
        return checked(value, first, second);
    }
    private static Jet checked(double value, double first, double second) {
        finite(value, "Perturbation value"); finite(first, "Perturbation first derivative"); finite(second, "Perturbation second derivative");
        return new Jet(value, first, second);
    }
    private static double[] smoothJet(double t) {
        double v = smooth5(t);
        if (t <= 0 || t >= 1) return new double[] {v, 0, 0};
        return new double[] {v, 30 * t * t * (1 - t) * (1 - t), 60 * t * (1 - t) * (1 - 2 * t)};
    }
    private static double smooth5(double t) {
        if (t <= 0) return 0; if (t >= 1) return 1;
        return t * t * t * (10 + t * (-15 + 6 * t));
    }
    private static double[] polyJet(double[] coefficients, double t) {
        return new double[] {horner(coefficients, t), horner(derivative(coefficients, 1), t),
                horner(derivative(coefficients, 2), t), horner(derivative(coefficients, 3), t)};
    }
    private static double horner(double[] coefficients, double t) {
        double value = 0;
        for (int i = coefficients.length - 1; i >= 0; i--) value = value * t + coefficients[i];
        return value;
    }
    private static double[] derivative(double[] coefficients, int order) {
        double[] out = coefficients.clone();
        for (int n = 0; n < order; n++) {
            double[] next = new double[Math.max(0, out.length - 1)];
            for (int i = 0; i < next.length; i++) { next[i] = out[i + 1] * (i + 1); finite(next[i], "Polynomial derivative"); }
            out = next;
        }
        return out.length == 0 ? new double[] {0} : out;
    }
    private static double[] product(double[] a, double[] b) {
        double[] out = new double[a.length + b.length - 1];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < b.length; j++) out[i + j] += a[i] * b[j];
        for (double v : out) finite(v, "Polynomial coefficient");
        return out;
    }
    private static double l1(double[] a) {
        double sum = 0; for (double v : a) { sum += Math.abs(v); finite(sum, "Polynomial bound"); } return sum;
    }

    private static final class OperationCurve extends Curve implements CurveEvaluation.Shape {
        private final Curve source;
        private final Recipe recipe;
        private final Config sourceConfig;
        private final double lower,upper,available,latest;
        OperationCurve(Curve source, Recipe recipe) {
            this.source=source;this.recipe=recipe;sourceConfig=source.config();lower=source.domainStart();upper=source.domainEnd();available=source.availableFrom();
            double start,end;boolean identity;
            if(recipe instanceof ImpulseRecipe r){start=r.at();end=start+r.attack()+r.relaxation();identity=r.magnitude()==0;}
            else{RedistributionRecipe r=(RedistributionRecipe)recipe;start=r.from();end=r.until();identity=r.magnitude()==0;}
            latest=Math.max(source.latestWriteTime(),start);
            evaluationNode=EvaluationGraph.unary(this,CurveEvaluation.node(source),start,end,identity,(self,parents,x,h,mode)->{if(mode==EvaluationGraph.MODE_ANALYTIC_JET){Analytic.Result p=(Analytic.Result)parents[0];return CompositionAnalytic.perturb(p,Operations.jet(recipe,x),sourceConfig,apply(p.sample(),x));}CurveEvaluation.requireScalar(mode);return apply((Sample)parents[0],x);});
        }
        @Override public Config config() { return sourceConfig; }
        @Override public double domainStart() { return lower; }
        @Override public double domainEnd() { return upper; }
        @Override public double availableFrom() { return available; }
        @Override double latestWriteTime(){return latest;}
        @Override public Sample sample(double x) { return CurveEvaluation.sample(this,x,null); }
        @Override public Sample at(double x, double[] latent) { return CurveEvaluation.sample(this,x,latent); }
        @Override public Curve fork(double at, String operation, Double width) { return CurveEvaluation.fork(this,at,operation,width); }
        @Override public String snapshotJson() { return CurveEvaluation.snapshot(this,null); }
        @Override public String tailJson(double from) { return CurveEvaluation.snapshot(this,from); }
        @Override public Vixen.Restored rebuild(Object[] parents){return new OperationCurve((Curve)parents[0],recipe);}
        @Override public Map<String,Object> replayFrame(Object[] parents){
            Map<String,Object> frame=new LinkedHashMap<>();frame.put("algorithm",ALGORITHM);frame.put("operation",recipe.type());frame.put("curve",parents[0]);frame.put("recipe",VixenJson.parse(recipeJson(recipe)));return frame;
        }
        @Override public EvaluationGraph.Node tailAlias(double from){
            double end=recipe instanceof ImpulseRecipe r?r.at()+r.attack()+r.relaxation():((RedistributionRecipe)recipe).until();return end<=from?CurveEvaluation.node(source):null;
        }
        private Sample apply(Sample p, double x) {
            double delta = Operations.jet(recipe, x).value();
            if (delta == 0) return p;
            double deviation = finite(p.unconstrainedDeviation(), "unconstrainedDeviation") + delta;
            finite(deviation, "Transformed deviation");
            if (deviation == p.unconstrainedDeviation()) return p;
            return finiteSample(p, deviation);
        }
    }

    private static String recipeJson(Recipe recipe) {
        if (recipe instanceof ImpulseRecipe r) return "{\"type\":\"impulse\",\"at\":" + VixenJson.num(r.at()) + ",\"magnitude\":"
                + VixenJson.num(r.magnitude()) + ",\"relaxation\":" + VixenJson.num(r.relaxation()) + ",\"attack\":" + VixenJson.num(r.attack()) + "}";
        RedistributionRecipe r = (RedistributionRecipe) recipe;
        StringBuilder b = new StringBuilder("{\"type\":\"redistribution\",\"from\":").append(VixenJson.num(r.from()))
                .append(",\"until\":").append(VixenJson.num(r.until())).append(",\"coefficients\":").append(vectorJson(r.coefficients))
                .append(",\"magnitude\":").append(VixenJson.num(r.magnitude())).append(",\"measure\":").append(VixenJson.quote(r.measure()))
                .append(",\"certificate\":{\"kind\":").append(VixenJson.quote(CERT_KIND)).append(",\"potential\":").append(VixenJson.quote(CERT_POTENTIAL))
                .append(",\"perturbation\":").append(VixenJson.quote(CERT_PERTURBATION)).append(",\"integral\":").append(VixenJson.quote(CERT_INTEGRAL)).append("}}");
        return b.toString();
    }
    private static String vectorJson(double[] values) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) b.append(i == 0 ? "" : ",").append(VixenJson.num(values[i]));
        return b.append(']').toString();
    }

    private static Sample finiteSample(Sample p, double deviation) {
        double base = finite(p.base(), "sample base");
        if (base <= 0) throw new IllegalArgumentException("Sample base must be positive");
        double unconstrained = base + base * deviation;
        finite(unconstrained, "Transformed value");
        boolean constrained = p.floorValue() != null || p.ceilingValue() != null;
        double value = constrained ? policy(unconstrained, p) : unconstrained;
        double actual = constrained ? (value - base) / base : deviation;
        double positive = StrictMath.exp(.55 * StrictMath.tanh(actual));
        finite(value, "Transformed sample value"); finite(actual, "Transformed sample deviation"); finite(positive, "Positive multiplier");
        return new Sample(p.x(), value, base, p.score(), p.guideScore(), p.rawDeviation(), p.guideDeviation(), p.guideValue(),
                p.stabilizationWeight(), actual, 1 + actual, .5 + .5 * StrictMath.tanh(actual), positive, base * positive,
                p.gate(), unconstrained, deviation, p.unconstrainedGuideValue(), p.unconstrainedGuideDeviation(), p.floorValue(),
                p.ceilingValue(), p.boundaryWidth(), value != unconstrained);
    }
    private static double policy(double value, Sample p) {
        Double lo = p.floorValue(), hi = p.ceilingValue(); double e = p.boundaryWidth();
        if (lo != null && hi != null && hi <= lo) return lo;
        if (lo != null) {
            if (value <= lo) value = lo;
            else if (e > 0 && value < lo + e) value = lo + e * boundaryRamp((value - lo) / e);
        }
        if (hi != null) {
            if (value >= hi) value = hi;
            else if (e > 0 && value > hi - e) value = hi - e * boundaryRamp((hi - value) / e);
        }
        return value;
    }
    private static double boundaryRamp(double t) {
        if (t <= 0) return 0; if (t >= 1) return t;
        return t * t * t * (6 + t * (-8 + 3 * t));
    }

    /** A group is a synchronized evaluation of two or more source curves, not a fork join. */
    public static final class Group implements Vixen.Restored,CurveEvaluation.Shape {
        private final List<Curve> sources, views;
        private final double strength, from, until, lo, hi, available, latest;
        private final EvaluationGraph.Node evaluationNode;
        private final boolean carried;
        private Group(List<Curve> sources, double strength, double from, double until, boolean carried) {
            if (sources == null || sources.size() < 2 || sources.size() > MAX_CURVES) throw new IllegalArgumentException("Coupling requires 2 to 32 curves");
            double lower = Double.NEGATIVE_INFINITY, upper = Double.POSITIVE_INFINITY;
            for (Curve c : sources) {
                if (c == null) throw new IllegalArgumentException("Coupling inputs must be Vixen curves");
                lower = Math.max(lower, Math.max(c.domainStart(),c.availableFrom())); upper = Math.min(upper, c.domainEnd());
            }
            if (!Double.isFinite(lower) || !Double.isFinite(upper) || upper <= lower) throw new IllegalArgumentException("Coupled curves have no common domain");
            finite(strength, "strength");
            if (strength < 0 || strength > 1) throw new IllegalArgumentException("strength must lie in [0,1]");
            finite(from, "window start"); finite(until, "window end"); width(from, until, "Coupling window");
            if (from < (carried ? 1 : lower) || until > upper)
                throw new IllegalArgumentException("Coupling window must have positive width inside the available domain");
            if (strength != 0) for (Curve c : sources) if (!c.config().unconstrained())
                throw new IllegalArgumentException("Nontrivial coupling requires sources without nonlinear floor or ceiling policies");
            this.sources = List.copyOf(sources); this.strength = strength; this.from = from; this.until = until;
            this.lo=lower;this.hi=upper;this.carried=carried;
            available=sources.stream().mapToDouble(Curve::availableFrom).max().orElse(1);
            latest=Math.max(from,sources.stream().mapToDouble(Curve::latestWriteTime).max().orElse(1));
            evaluationNode=EvaluationGraph.multi(this,sources.stream().map(CurveEvaluation::node).toList(),(self,parents,x,h,mode)->{if(mode==EvaluationGraph.MODE_ANALYTIC_JET){Analytic.Result[] jets=Arrays.copyOf(parents,parents.length,Analytic.Result[].class);Sample[] samples=Arrays.stream(jets).map(Analytic.Result::sample).toArray(Sample[]::new);return CompositionAnalytic.couple(jets,couplingWeightJet(x),compute(samples,x));}CurveEvaluation.requireScalar(mode);return compute(Arrays.copyOf(parents,parents.length,Sample[].class),x);});
            List<Curve> made = new ArrayList<>();
            for (int i = 0; i < sources.size(); i++) made.add(new CouplingView(this, i));
            this.views = List.copyOf(made);
        }
        public double strength() { return strength; }
        public double[] window() { return new double[] {from, until}; }
        public double[] domain() { return new double[] {lo, hi}; }
        public List<Curve> curves() { return views; }
        public double availableFrom() { return available; }
        public Sample[] sample(double x) { return evaluate(x, null); }
        public Sample[] at(double x, double[] latent) { return evaluate(x, latent); }
        public Sample[][] sampleRange(double start, double end, int count) {
            checkRange(start, end, count, lo, hi); Sample[][] out = new Sample[count][];
            for (int i = 0; i < count; i++) out[i] = sample(start + (end - start) * i / (count - 1));
            return out;
        }
        private Sample[] evaluate(double x, double[] latent) {
            finite(x, "sample coordinate");
            if (x < lo || x > hi) throw new IllegalArgumentException("x outside coupled domain");
            return (Sample[])CurveEvaluation.evaluate(evaluationNode,x,latent);
        }
        private Operations.Jet couplingWeightJet(double x){
            if(x<=from||x>=until)return new Operations.Jet(0,0,0);
            double mid=from+(until-from)/2;boolean left=x<=mid;
            double width=left?mid-from:until-mid,t=left?(x-from)/width:(until-x)/width,rate=(left?1:-1)/width;
            return new Operations.Jet(strength*gate(x,from,until),strength*30*t*t*(t-1)*(t-1)*rate,strength*60*t*(t-1)*(2*t-1)*rate*rate);
        }
        public Analytic.Result[] analyticJet(double x){return analyticJet(x,null);}
        public Analytic.Result[] analyticJet(double x,double[] h){
            if(!Double.isFinite(x)||x<lo||x>hi||x<available)throw new IllegalArgumentException("x outside coupled analytic domain");
            return (Analytic.Result[])CurveEvaluation.analytic(evaluationNode,x,h==null?null:Config.vector(h,1_000_000));
        }
        private Sample[] compute(Sample[] parents,double x){
            double amount = strength * gate(x, from, until);
            if (amount == 0) return parents;
            double[] deviations = new double[parents.length]; double max = 0;
            for (int i = 0; i < parents.length; i++) { deviations[i] = finite(parents[i].deviation(), "deviation"); max = Math.max(max, Math.abs(deviations[i])); }
            double mean = Floating.mean(deviations);
            Sample[] out = new Sample[parents.length];
            for (int i = 0; i < out.length; i++) {
                if(deviations[i]==mean){out[i]=parents[i];continue;}
                double d=(1-amount)*deviations[i]+amount*mean;
                if(!Double.isFinite(d))d=Floating.sumProducts(new double[][]{{1-amount,deviations[i]},{amount,mean}});
                finite(d, "Coupled deviation"); out[i] = deriveUnconstrained(parents[i], d);
            }
            return out;
        }
        @Override public String snapshotJson(){return CurveEvaluation.snapshot(evaluationNode,null);}
        @Override public String tailJson(double checkpoint){
            if(!Double.isFinite(checkpoint)||checkpoint<lo||checkpoint<available||checkpoint>hi)throw new IllegalArgumentException("Checkpoint outside group domain");return CurveEvaluation.snapshot(evaluationNode,checkpoint);
        }
        @Override public boolean canFork(){return false;}
        @Override public Vixen.Restored rebuild(Object[] parents){throw new UnsupportedOperationException("Fork input lineages before coupling");}
        @Override public Map<String,Object> replayFrame(Object[] parents){
            Map<String,Object> frame=new LinkedHashMap<>();frame.put("algorithm",ALGORITHM);frame.put("operation","coupling");frame.put("curves",List.of(parents));frame.put("strength",strength);frame.put("window",List.of(from,until));return frame;
        }

    }

    private static final class CouplingView extends Curve implements CurveEvaluation.Shape {
        private final Group group; private final int index;
        private final Config sourceConfig;
        CouplingView(Group group, int index) {
            this.group=group;this.index=index;sourceConfig=group.sources.get(index).config();
            evaluationNode=EvaluationGraph.unary(this,group.evaluationNode,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,false,(self,parents,x,h,mode)->{if(mode==EvaluationGraph.MODE_ANALYTIC_JET)return ((Analytic.Result[])parents[0])[index];CurveEvaluation.requireScalar(mode);return ((Sample[])parents[0])[index];});
        }
        @Override public Config config() { return sourceConfig; }
        @Override public double domainStart() { return group.lo; }
        @Override public double domainEnd() { return group.hi; }
        @Override public double availableFrom() { return group.availableFrom(); }
        @Override double latestWriteTime(){return group.latest;}
        @Override public Sample sample(double x) { return CurveEvaluation.sample(this,x,null); }
        @Override public Sample at(double x, double[] latent) { return CurveEvaluation.sample(this,x,latent); }
        @Override public Curve fork(double at, String operation, Double width) { return CurveEvaluation.fork(this,at,operation,width); }
        @Override public Sample[] sampleRange(double from, double until, int count) {
            checkRange(from, until, count, group.lo, group.hi); Sample[] out = new Sample[count];
            for (int i = 0; i < count; i++) out[i] = sample(from + (until - from) * i / (count - 1));
            return out;
        }
        @Override public String snapshotJson(){return CurveEvaluation.snapshot(this,null);}
        @Override public String tailJson(double from){return CurveEvaluation.snapshot(this,from);}
        @Override public boolean canFork(){return false;}
        @Override public Vixen.Restored rebuild(Object[] parents){throw new UnsupportedOperationException("Fork input lineages before coupling");}
        @Override public Map<String,Object> replayFrame(Object[] parents){
            Map<String,Object> frame=new LinkedHashMap<>();frame.put("algorithm",ALGORITHM);frame.put("operation","couplingView");frame.put("group",parents[0]);frame.put("index",index);return frame;
        }
        @Override public EvaluationGraph.Node tailAlias(double from){return group.until<=from?CurveEvaluation.node(group.sources.get(index)):null;}

    }

    public static Group couple(List<Curve> curves, double strength, double from, double until) {
        return new Group(curves, strength, from, until, false);
    }
    private static double gate(double x, double from, double until) {
        if (x <= from || x >= until) return 0;
        double mid = from + (until - from) / 2;
        return x <= mid ? smooth5((x - from) / (mid - from)) : smooth5((until - x) / (until - mid));
    }
    private static double stableMean(double[] values, double max) {
        if (max == 0) return 0;
        double sum = 0, correction = 0;
        for (double value : values) {
            double x = value / max, next = sum + x;
            correction += Math.abs(sum) >= Math.abs(x) ? (sum - next) + x : (x - next) + sum;
            sum = next;
        }
        return ((sum + correction) / values.length) * max;
    }
    private static Sample deriveUnconstrained(Sample p, double deviation) {
        return finiteSample(p,deviation);
    }
    private static void checkRange(double from, double until, int count, double lo, double hi) {
        finite(from, "range start"); finite(until, "range end");
        if (count < 2 || count > 100001 || from < lo || until > hi || until < from) throw new IllegalArgumentException("Invalid sample range");
    }
    static final class InfluenceCurve extends Curve implements CurveEvaluation.Shape {
        private final Curve source; private final Influence influence;
        private final Config sourceConfig;
        private final double lower,upper,available,latest;
        InfluenceCurve(Curve source,Influence influence){this(source,influence,false);}
        InfluenceCurve(Curve source, Influence influence,boolean carried) {
            this.source=source;this.influence=influence;influence.validate(carried?1:source.domainStart(),source.domainEnd());
            sourceConfig=source.config();lower=source.domainStart();upper=influence.isWindowed()?source.domainEnd():Math.min(source.domainEnd(),influence.guide().hi());available=source.availableFrom();latest=Math.max(source.latestWriteTime(),influence.at());
            if(available>upper)throw new IllegalArgumentException("Influence ends before available history");
            evaluationNode=EvaluationGraph.unary(this,CurveEvaluation.node(source),influence.at(),influence.end(),influence.amount()==0,(self,parents,x,h,mode)->{if(mode==EvaluationGraph.MODE_ANALYTIC_JET){Analytic.Result p=(Analytic.Result)parents[0];return CompositionAnalytic.influence(p,influence,x,sourceConfig,influence.apply(p.sample(),x,sourceConfig));}CurveEvaluation.requireScalar(mode);return influence.apply((Sample)parents[0],x,sourceConfig);});
        }
        @Override public Config config() { return sourceConfig; }
        @Override public double domainStart() { return lower; }
        @Override public double domainEnd() { return upper; }
        @Override public double availableFrom() { return available; }
        @Override double latestWriteTime(){return latest;}
        @Override public Sample sample(double x) { return CurveEvaluation.sample(this,x,null); }
        @Override public Sample at(double x, double[] latent) { return CurveEvaluation.sample(this,x,latent); }
        @Override public Curve fork(double at, String operation, Double width) { return CurveEvaluation.fork(this,at,operation,width); }
        @Override public String snapshotJson() { return CurveEvaluation.snapshot(this,null); }
        @Override public Vixen.Restored rebuild(Object[] parents){return Vixen.replayInfluence((Curve)parents[0],influence);}
        @Override public Map<String,Object> replayFrame(Object[] parents){return VixenJson.influenceSnapshotFrame(influence,parents[0]);}
        @Override public EvaluationGraph.Node tailAlias(double from){return influence.end()<=from?CurveEvaluation.node(source):null;}
        @Override public String tailJson(double from) { return CurveEvaluation.snapshot(this,from); }
        private String influenceFrame(String child) {
            String algorithm = influence.algorithm();
            StringBuilder b = new StringBuilder("{\"algorithm\":").append(VixenJson.quote(algorithm)).append(",\"amount\":").append(VixenJson.num(influence.amount()))
                    .append(",\"at\":").append(VixenJson.num(influence.at())).append(",\"curve\":").append(child).append(",\"guide\":")
                    .append(VixenJson.guideText(influence.guide())).append(",\"handoffWidth\":").append(VixenJson.num(influence.handoffWidth()));
            if (influence.mode() == Influence.Mode.ADD) b.append(",\"mode\":\"add\"");
            if (influence.extended()) {
                b.append(",\"reference\":").append(VixenJson.quote(influence.reference().word));
                if (influence.isWindowed()) b.append(",\"releaseWidth\":").append(VixenJson.num(influence.releaseWidth())).append(",\"until\":").append(VixenJson.num(influence.until()));
            }
            return b.append('}').toString();
        }
    }

    static Vixen.Restored restoreSnapshot(Map<String, Object> m, Function<Object, Vixen.Restored> restore) {
        Object opValue = m.get("operation");
        if (!(opValue instanceof String op)) throw new IllegalArgumentException("Operation kind must be text");
        if (op.equals("impulse") || op.equals("redistribution")) {
            fields(m, Set.of("algorithm", "operation", "curve", "recipe"), Set.of("algorithm", "operation", "curve", "recipe"), "operation snapshot");
            Curve source = Vixen.requireCurve(restore.apply(m.get("curve")));
            Recipe recipe;
            if (op.equals("impulse")) {
                Map<String, Object> r = fields(m.get("recipe"), Set.of("type", "at", "magnitude", "relaxation", "attack"),
                        Set.of("type", "at", "magnitude", "relaxation", "attack"), "impulse recipe");
                if (!"impulse".equals(r.get("type"))) throw new IllegalArgumentException("Operation and recipe disagree");
                recipe = impulseRecipe(finite(r.get("at"), "at"), finite(r.get("magnitude"), "magnitude"),
                        finite(r.get("relaxation"), "relaxation"), finite(r.get("attack"), "attack"));
            } else recipe = parseRedistribution(castMap(m.get("recipe"), "redistribution recipe"));
            validateDomain(source, recipe, true);
            if (recipe instanceof RedistributionRecipe r) validateMeasure(source, r);
            return new OperationCurve(source, recipe);
        }
        if (op.equals("coupling")) {
            fields(m, Set.of("algorithm", "operation", "curves", "strength", "window"), Set.of("algorithm", "operation", "curves", "strength", "window"), "coupling snapshot");
            if (!(m.get("curves") instanceof List<?> list) || list.size() < 2 || list.size() > MAX_CURVES)
                throw new IllegalArgumentException("Invalid coupling source count");
            List<Curve> curves = new ArrayList<>(); for (Object item : list) curves.add(Vixen.requireCurve(restore.apply(item)));
            double[] w = pair(m.get("window"), "window");
            return new Group(curves, finite(m.get("strength"), "strength"), w[0], w[1], true);
        }
        if (op.equals("couplingView")) {
            fields(m, Set.of("algorithm", "operation", "group", "index"), Set.of("algorithm", "operation", "group", "index"), "coupling view snapshot");
            Vixen.Restored restored = restore.apply(m.get("group"));
            if (!(restored instanceof Group group)) throw new IllegalArgumentException("Invalid coupling view group");
            Object indexValue = m.get("index");
            if (!(indexValue instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.intValue() < 0 || n.intValue() >= group.curves().size())
                throw new IllegalArgumentException("Invalid coupling view index");
            return group.curves().get(n.intValue());
        }
        throw new IllegalArgumentException("Unknown operation snapshot kind");
    }

    private static double[] pair(Object value, String name) {
        if (!(value instanceof List<?> list) || list.size() != 2) throw new IllegalArgumentException(name + " must contain two endpoints");
        return new double[] {finite(list.get(0), name), finite(list.get(1), name)};
    }
    private static double[] numbers(Object value, String name) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(name + " must be an array");
        double[] out = new double[list.size()]; for (int i = 0; i < out.length; i++) out[i] = finite(list.get(i), name); return out;
    }
    private static double finite(Object value, String name) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException(name + " must be finite");
        return n.doubleValue();
    }
    private static String text(Object value, String name) {
        if (!(value instanceof String s)) throw new IllegalArgumentException(name + " must be text"); return s;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> castMap(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(name + " must be an object"); return (Map<String, Object>) value;
    }
    private static Map<String, Object> fields(Object value, Set<String> allowed, Set<String> required, String name) {
        Map<String, Object> m = castMap(value, name);
        for (String key : m.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown " + name + " field: " + key);
        for (String key : required) if (!m.containsKey(key) || m.get(key) == null) throw new IllegalArgumentException("Missing " + name + " field: " + key);
        return m;
    }
}