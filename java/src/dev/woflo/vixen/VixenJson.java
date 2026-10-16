package dev.woflo.vixen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Vixen's replay schemas as JSON text: {@code vixen-hyperfield-c2/4} snapshots (configuration and lineage), {@code vixen-influence-c2/1}
 * (and the port's {@code /2} for windowed and deviation-unit influences), {@code vixen-guide/1} and the tail ({@code vixen-tail-c2/1}).
 * The reader is strict as Vixen's is: an unknown field, a null control or a cycle is refused, and a snapshot is replayed, never executed.
 *
 * <p>This carries its own small JSON codec rather than {@code kernel/json}: that tree holds exact decimals of at most nine places, and a
 * Vixen configuration is made of doubles (a seed, a coordinate, a tempo) that must round-trip bit for bit. Numbers are written in the
 * shortest form that reads back to the same double.
 */
public final class VixenJson {
    private VixenJson() {
    }

    // ----- writing ----------------------------------------------------------------------------------------------------------------

    /** A double as JSON text: an integral value as an integer, else the shortest decimal that reads back to it. */
    public static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("JSON has no non-finite numbers");
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            long l = (long) d;
            return Long.toString(l);
        }
        return Double.toString(d);
    }

    /** A string as JSON text, escaped minimally (the quote, the backslash and control characters). */
    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String seedText(Seed s) {
        return s.isText() ? quote(s.text()) : Long.toString(s.unsigned());
    }

    private static String vec(double[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ",").append(num(v[i]));
        }
        return sb.append(']').toString();
    }

    /** A configuration, keys in sorted order. */
    public static String configText(Config c) {
        return "{\"boundarySoftness\":" + num(c.boundarySoftness()) + ",\"ceilingMargin\":" + (c.ceilingMargin() == null ? "null" : num(c.ceilingMargin()))
                + ",\"contrast\":" + num(c.contrast()) + ",\"floor\":" + quote(c.floor().word) + ",\"introVariation\":" + num(c.introVariation())
                + ",\"latent\":" + vec(c.latentRef()) + ",\"openingEndpoint\":" + num(c.openingEndpoint()) + ",\"ramp\":" + num(c.ramp()) + ",\"seed\":"
                + seedText(c.seed()) + ",\"stabilizer\":" + num(c.stabilizer()) + ",\"strength\":" + num(c.strength()) + ",\"tempo\":" + num(c.tempo())
                + ",\"velocity\":" + vec(c.velocityRef()) + "}";
    }

    /** A guide, keys in sorted order. */
    public static String guideText(Guide g) {
        if (g instanceof Guide.Polynomial p) {
            return "{\"center\":" + num(p.center()) + ",\"coefficients\":" + vec(p.coefficients()) + ",\"domain\":[" + num(p.lo()) + "," + num(p.hi())
                    + "],\"format\":\"vixen-guide/1\",\"kind\":\"polynomial\",\"scale\":" + num(p.scale()) + "}";
        }
        Guide.Sampled s = (Guide.Sampled) g;
        StringBuilder sb = new StringBuilder("{\"format\":\"vixen-guide/1\",\"kind\":\"quintic\",\"knots\":[");
        List<Guide.Knot> knots = s.knots();
        for (int i = 0; i < knots.size(); i++) {
            Guide.Knot k = knots.get(i);
            sb.append(i == 0 ? "" : ",").append("{\"d1\":").append(num(k.d1())).append(",\"d2\":").append(num(k.d2())).append(",\"x\":").append(num(k.x()))
                    .append(",\"y\":").append(num(k.y())).append('}');
        }
        return sb.append("]}").toString();
    }

    /** An influence's own fields (without the curve it hangs on), keys in sorted order. */
    public static String influenceText(Influence i) {
        StringBuilder sb = new StringBuilder("{\"amount\":" + num(i.amount()) + ",\"at\":" + num(i.at()) + ",\"guide\":" + guideText(i.guide())
                + ",\"handoffWidth\":" + num(i.handoffWidth()));
        if (i.mode() == Influence.Mode.ADD) sb.append(",\"mode\":\"add\"");
        if (i.extended()) {
            sb.append(",\"reference\":").append(quote(i.reference().word));
            if (i.isWindowed()) {
                sb.append(",\"releaseWidth\":").append(num(i.releaseWidth())).append(",\"until\":").append(num(i.until()));
            }
        }
        return sb.append('}').toString();
    }

    private static String forksText(List<Timeline.Snapshot.Fork> forks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < forks.size(); i++) {
            Timeline.Snapshot.Fork f = forks.get(i);
            sb.append(i == 0 ? "" : ",").append("{\"at\":").append(num(f.at())).append(",\"handoffWidth\":").append(num(f.handoffWidth()))
                    .append(",\"operation\":").append(quote(f.operation())).append('}');
        }
        return sb.append(']').toString();
    }

    /** A snapshot as Vixen writes it: the lineage's snapshot, wrapped in one influence frame for each influence in order. */
    public static String snapshotText(Timeline.Snapshot s) {
        if(s.checkpoint()!=null){Timeline.Snapshot.Checkpoint c=s.checkpoint();return tailText(Tail.of(s.config(),c.seed(),c.from(),c.folded(),c.witness(),s.forks(),s.influences(),s.coherent()));}
        String text = "{\"algorithm\":\"" + (s.coherent()?Timeline.COHERENT_ALGORITHM:Leaf.ALGORITHM) + "\",\"config\":" + configText(s.config()) + ",\"lineage\":" + forksText(s.forks()) + "}";
        return wrapInfluences(text,s.influences());
    }

    @SuppressWarnings("unchecked")
    static Map<String,Object> influenceSnapshotFrame(Influence influence,Object parent) {
        Map<String,Object> frame=(Map<String,Object>)parse(wrapInfluences("null",List.of(influence)));frame.put("curve",parent);return frame;
    }

    private static String wrapInfluences(String text,List<Influence> influences) {
        for (Influence i : influences) {
            String algorithm = i.algorithm();
            // the frame's own fields wrap the curve it hangs on, keys in sorted order: algorithm, amount, at, curve, guide, ...
            StringBuilder sb = new StringBuilder("{\"algorithm\":\"" + algorithm + "\",\"amount\":" + num(i.amount()) + ",\"at\":" + num(i.at())
                    + ",\"curve\":" + text + ",\"guide\":" + guideText(i.guide()) + ",\"handoffWidth\":" + num(i.handoffWidth()));
            if (i.mode() == Influence.Mode.ADD) sb.append(",\"mode\":\"add\"");
            if (i.extended()) {
                sb.append(",\"reference\":").append(quote(i.reference().word));
                if (i.isWindowed()) {
                    sb.append(",\"releaseWidth\":").append(num(i.releaseWidth())).append(",\"until\":").append(num(i.until()));
                }
            }
            text = sb.append('}').toString();
        }
        return text;
    }

    /** A tail as JSON text. */
    public static String tailText(Tail t) {
        if(t.coherent())return coherentTailText(t);
        StringBuilder sb = new StringBuilder("{\"algorithm\":\"" + Tail.ALGORITHM + "\",\"config\":" + configText(t.config()) + ",\"folded\":" + t.folded()
                + ",\"from\":" + num(t.from()) + ",\"influences\":[");
        for (int i = 0; i < t.influences().size(); i++) {
            sb.append(i == 0 ? "" : ",").append(influenceText(t.influences().get(i)));
        }
        sb.append("],\"leafSeed\":").append(seedText(t.leafSeed())).append(",\"lineage\":[");
        for (int i = 0; i < t.open().size(); i++) {
            Timeline.Entry e = t.open().get(i);
            sb.append(i == 0 ? "" : ",").append("{\"at\":").append(num(e.at())).append(",\"handoffWidth\":").append(num(e.handoffWidth()))
                    .append(",\"operation\":").append(quote(e.operation())).append('}');
        }
        return sb.append("],\"witness\":").append(quote(t.witness())).append('}').toString();
    }

    private static String coherentTailText(Tail t){
        List<Timeline.Snapshot.Fork> forks=new ArrayList<>();
        for(Timeline.Entry e:t.open())forks.add(new Timeline.Snapshot.Fork(e.at(),e.operation(),e.handoffWidth()));
        String text="{\"algorithm\":"+quote(Tail.COHERENT_ALGORITHM)+",\"from\":"+num(t.from())+",\"config\":"+configText(t.config())
            +",\"seed\":"+seedText(t.leafSeed())+",\"leafSeed\":"+(t.leafSeed().u32()&0xffffffffL)+",\"foldedCount\":"+t.folded()
            +",\"witness\":"+quote(t.witness())+",\"lineage\":"+forksText(forks)+"}";
        return wrapInfluences(text,t.influences());
    }

    // ----- reading ----------------------------------------------------------------------------------------------------------------

    /** The reader's tree: Map (LinkedHashMap), List, String, Double, Boolean or null. */
    public static Object parse(String text) {
        Reader r = new Reader(text);
        Object v = r.value(0);
        r.space();
        if (r.i != text.length()) {
            throw new IllegalArgumentException("Trailing text after the JSON value");
        }
        return v;
    }

    private static final class Reader {
        final String s;
        int i;

        Reader(String s) {
            this.s = s;
        }

        void space() {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\n' || s.charAt(i) == '\r' || s.charAt(i) == '\t')) {
                i++;
            }
        }

        Object value(int depth){return value(depth,false);}

        Object value(int depth,boolean exactCounter) {
            if (depth > 200) {
                throw new IllegalArgumentException("JSON nests too deeply");
            }
            space();
            if (i >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                space();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    space();
                    String k = string();
                    space();
                    expect(':');
                    if (m.containsKey(k)) {
                        throw new IllegalArgumentException("Duplicate key " + k);
                    }
                    m.put(k, value(depth + 1,"folded".equals(k)));
                    space();
                    if (s.charAt(i) == ',') {
                        i++;
                    } else {
                        expect('}');
                        return m;
                    }
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                space();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value(depth + 1));
                    space();
                    if (s.charAt(i) == ',') {
                        i++;
                    } else {
                        expect(']');
                        return l;
                    }
                }
            }
            if (c == '"') {
                return string();
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (start == i) {
                throw new IllegalArgumentException("Unexpected JSON character at " + i);
            }
            String token=s.substring(start,i);
            if(exactCounter){try{return Long.valueOf(new java.math.BigDecimal(token).longValueExact());}catch(ArithmeticException|NumberFormatException ignored){}}
            return Double.valueOf(Double.parseDouble(token));
        }

        void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at " + i);
            }
            i++;
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw new IllegalArgumentException("Unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalArgumentException("Bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o, String what) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException(what + " must be an object");
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> arr(Object o, String what) {
        if (!(o instanceof List)) {
            throw new IllegalArgumentException(what + " must be an array");
        }
        return (List<Object>) o;
    }

    private static double dbl(Object o, String what) {
        if (!(o instanceof Number number)) {
            throw new IllegalArgumentException(what + " must be a number");
        }
        return number.doubleValue();
    }

    private static void only(Map<String, Object> m, String what, Set<String> allowed) {
        for (String k : m.keySet()) {
            if (!allowed.contains(k)) {
                throw new IllegalArgumentException("Unknown " + what + " field: " + k);
            }
        }
    }

    private static void all(Map<String, Object> m, String what, Set<String> required) {
        for (String k : required) {
            if (!m.containsKey(k) || m.get(k) == null) {
                throw new IllegalArgumentException("Missing " + what + " field: " + k);
            }
        }
    }

    private static final Set<String> CONFIG_KEYS = Set.of("seed", "openingEndpoint", "introVariation", "floor", "ceilingMargin", "boundarySoftness",
            "stabilizer", "strength", "ramp", "tempo", "contrast", "latent", "velocity");

    private static Seed seedOf(Object o) {
        if (o instanceof String s) {
            return Seed.of(s);
        }
        if (o instanceof Number number) {
            double d=number.doubleValue();
            if (d != Math.rint(d)) {
                throw new IllegalArgumentException("Numeric seed must be uint32");
            }
            return Seed.of((long) (double) d);
        }
        throw new IllegalArgumentException("Seed must be text or uint32");
    }

    private static double[] vector(Object o, String what) {
        List<Object> l = arr(o, what);
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = dbl(l.get(i), what);
        }
        return v;
    }

    /** A configuration; a key left out takes Vixen's default. */
    public static Config config(Object o) {
        Map<String, Object> m = obj(o, "config");
        only(m, "config", CONFIG_KEYS);
        Config d = Config.defaults();
        Seed seed = m.containsKey("seed") ? seedOf(m.get("seed")) : d.seed();
        Double ceiling = d.ceilingMargin();
        if (m.containsKey("ceilingMargin")) {
            ceiling = m.get("ceilingMargin") == null ? null : Double.valueOf(dbl(m.get("ceilingMargin"), "ceilingMargin"));
        }
        return new Config(seed, num(m, "openingEndpoint", d.openingEndpoint()), num(m, "introVariation", d.introVariation()),
                m.containsKey("floor") ? Config.Floor.parse((String) m.get("floor")) : d.floor(), ceiling, num(m, "boundarySoftness", d.boundarySoftness()),
                num(m, "stabilizer", d.stabilizer()), num(m, "strength", d.strength()), num(m, "ramp", d.ramp()), num(m, "tempo", d.tempo()),
                num(m, "contrast", d.contrast()), m.containsKey("latent") ? vector(m.get("latent"), "latent") : d.latent(),
                m.containsKey("velocity") ? vector(m.get("velocity"), "velocity") : d.velocity());
    }

    private static double num(Map<String, Object> m, String key, double fallback) {
        return m.containsKey(key) ? dbl(m.get(key), key) : fallback;
    }

    /** A guide spec. */
    public static Guide guide(Object o) {
        Map<String, Object> m = obj(o, "guide");
        if (!Guide.FORMAT.equals(m.get("format"))) {
            throw new IllegalArgumentException("Invalid guide");
        }
        if ("quintic".equals(m.get("kind"))) {
            only(m, "guide", Set.of("format", "kind", "knots"));
            all(m, "guide", Set.of("knots"));
            List<Guide.Knot> knots = new ArrayList<>();
            for (Object k : arr(m.get("knots"), "knots")) {
                Map<String, Object> km = obj(k, "knot");
                only(km, "knot", Set.of("x", "y", "d1", "d2"));
                all(km, "knot", Set.of("x", "y", "d1", "d2"));
                knots.add(new Guide.Knot(dbl(km.get("x"), "x"), dbl(km.get("y"), "y"), dbl(km.get("d1"), "d1"), dbl(km.get("d2"), "d2")));
            }
            return new Guide.Sampled(knots);
        }
        if (!"polynomial".equals(m.get("kind"))) {
            throw new IllegalArgumentException("Invalid guide");
        }
        only(m, "guide", Set.of("format", "kind", "coefficients", "center", "scale", "domain"));
        all(m, "guide", Set.of("coefficients", "center", "scale", "domain"));
        List<Object> dom = arr(m.get("domain"), "domain");
        if (dom.size() != 2) {
            throw new IllegalArgumentException("Guide domain requires two endpoints");
        }
        return new Guide.Polynomial(vector(m.get("coefficients"), "coefficients"), dbl(m.get("center"), "center"), dbl(m.get("scale"), "scale"),
                dbl(dom.get(0), "domain"), dbl(dom.get(1), "domain"));
    }

    static Influence influenceFrame(Map<String, Object> m, boolean extended) {
        Object algorithm=m.get("algorithm");
        boolean bare=!m.containsKey("algorithm");
        boolean additive=bare ? m.containsKey("mode") : Influence.ALGORITHM_ADDITIVE.equals(algorithm);
        if (!bare && !Influence.ALGORITHM.equals(algorithm) && !Influence.ALGORITHM_WINDOWED.equals(algorithm) && !additive)
            throw new IllegalArgumentException("Unsupported influence algorithm");
        java.util.Set<String> keys=new java.util.HashSet<>(Set.of("at","amount","handoffWidth","guide"));
        if (!bare) { keys.add("algorithm"); keys.add("curve"); }
        if (extended || additive) { keys.add("reference"); keys.add("until"); keys.add("releaseWidth"); }
        if (additive) keys.add("mode");
        only(m,"influence",keys);
        all(m,"influence",Set.of("at","amount","handoffWidth","guide"));
        if (extended || additive) all(m,"influence",Set.of("reference"));
        if (additive && !"add".equals(m.get("mode"))) throw new IllegalArgumentException("Schema 3 requires additive mode");
        if (m.containsKey("until") != m.containsKey("releaseWidth")) throw new IllegalArgumentException("Window requires until and releaseWidth");
        if (!(m.getOrDefault("reference","value") instanceof String word)) throw new IllegalArgumentException("reference must be text");
        Influence.Reference ref=Influence.Reference.parse(word);
        if (!bare && extended && !additive && !m.containsKey("until") && ref==Influence.Reference.VALUE)
            throw new IllegalArgumentException("Schema 2 requires an extended influence recipe");
        Guide g=guide(m.get("guide"));
        return new Influence(dbl(m.get("at"),"at"),dbl(m.get("amount"),"amount"),dbl(m.get("handoffWidth"),"handoffWidth"),g,ref,
                m.containsKey("until") ? dbl(m.get("until"),"until") : Double.POSITIVE_INFINITY,
                m.containsKey("releaseWidth") ? dbl(m.get("releaseWidth"),"releaseWidth") : 0,
                additive ? Influence.Mode.ADD : Influence.Mode.BLEND);
    }

    private static List<Timeline.Snapshot.Fork> forks(Object o) {
        List<Timeline.Snapshot.Fork> out = new ArrayList<>();
        for (Object e : arr(o, "lineage")) {
            Map<String, Object> m = obj(e, "fork");
            only(m, "fork", Set.of("at", "operation", "handoffWidth"));
            all(m, "fork", Set.of("at", "operation", "handoffWidth"));
            if (!(m.get("operation") instanceof String op)) {
                throw new IllegalArgumentException("operation must be text");
            }
            out.add(new Timeline.Snapshot.Fork(dbl(m.get("at"), "at"), op, dbl(m.get("handoffWidth"), "handoffWidth")));
        }
        return out;
    }

    /** Reads a snapshot (current, or Vixen's archived {@code c2/1} to {@code c2/3} configurations) into the timeline it replays. */
    public static Timeline restore(String text) {
        return restore(parse(text));
    }

    public static Timeline restore(Object tree) {
        Map<String, Object> top = obj(tree, "snapshot");
        List<Map<String, Object>> frames = new ArrayList<>();
        Map<String, Object> s = top;
        while (s != null && (Influence.ALGORITHM.equals(s.get("algorithm")) || Influence.ALGORITHM_WINDOWED.equals(s.get("algorithm")) || Influence.ALGORITHM_ADDITIVE.equals(s.get("algorithm")))) {
            for (String k : List.of("algorithm", "curve", "guide", "at", "amount", "handoffWidth")) {
                if (!s.containsKey(k) || s.get(k) == null) {
                    throw new IllegalArgumentException("Invalid influence snapshot fields");
                }
            }
            frames.add(s);
            s = obj(s.get("curve"), "curve");
        }
        if (!s.containsKey("algorithm") || !s.containsKey("config")) {
            throw new IllegalArgumentException("Snapshot with configuration required");
        }
        if(Tail.ALGORITHM.equals(s.get("algorithm"))||Tail.COHERENT_ALGORITHM.equals(s.get("algorithm"))){
            Timeline restored=tail(Vixen.stringifyTree(s)).restore();
            for(int i=frames.size()-1;i>=0;i--){Map<String,Object> frame=frames.get(i);restored=restored.replayInfluence(influenceFrame(frame,!Influence.ALGORITHM.equals(frame.get("algorithm"))));}
            return restored;
        }
        only(s, "snapshot", Set.of("algorithm", "config", "lineage"));
        Timeline t;
        Object algorithm = s.get("algorithm");
        if (Leaf.ALGORITHM.equals(algorithm)||Timeline.COHERENT_ALGORITHM.equals(algorithm)) {
            Timeline.Snapshot snap = new Timeline.Snapshot(config(s.get("config")), s.containsKey("lineage") && s.get("lineage") != null
                    ? forks(s.get("lineage")) : List.of(), List.of(),Timeline.COHERENT_ALGORITHM.equals(algorithm));
            t = Timeline.restore(snap);
        } else if (List.of("vixen-hyperfield-c2/1", "vixen-hyperfield-c2/2", "vixen-hyperfield-c2/3").contains(algorithm)) {
            if (s.containsKey("lineage")) {
                throw new IllegalArgumentException("Old snapshots cannot contain timeline features");
            }
            Map<String, Object> cm = new LinkedHashMap<>(obj(s.get("config"), "config"));
            if (!"vixen-hyperfield-c2/3".equals(algorithm)) {
                cm.remove("floor");
                cm.remove("ceilingMargin");
            }
            t = Timeline.create(config(cm));
        } else {
            throw new IllegalArgumentException("Algorithm mismatch: use the matching archived sampler");
        }
        for (int i = frames.size() - 1; i >= 0; i--) {
            Map<String, Object> f = frames.get(i);
            t = t.replayInfluence(influenceFrame(f, !Influence.ALGORITHM.equals(f.get("algorithm"))));
        }
        return t;
    }

    /** Reads a tail. */
    public static Tail tail(String text) {
        Map<String, Object> m = obj(parse(text), "tail");
        if(Tail.COHERENT_ALGORITHM.equals(m.get("algorithm")))return coherentTail(m);
        only(m, "tail", Set.of("algorithm", "config", "folded", "from", "influences", "leafSeed", "lineage", "witness"));
        all(m, "tail", Set.of("config", "folded", "from", "influences", "leafSeed", "lineage", "witness"));
        if (!Tail.ALGORITHM.equals(m.get("algorithm"))) {
            throw new IllegalArgumentException("Algorithm mismatch: not a tail");
        }
        Config config = config(m.get("config"));
        Seed leaf = seedOf(m.get("leafSeed"));
        List<Timeline.Entry> open = new ArrayList<>();
        Seed parent = leaf;
        for (Timeline.Snapshot.Fork f : forks(m.get("lineage"))) {
            int seed = Hash.forkSeed(parent, f.operation(), f.at());
            open.add(new Timeline.Entry(f.at(), f.operation(), f.handoffWidth(), seed));
            parent = Seed.of(seed & 0xffffffffL);
        }
        List<Influence> influences = new ArrayList<>();
        for (Object o : arr(m.get("influences"), "influences")) {
            Map<String, Object> im = obj(o, "influence");
            influences.add(influenceFrame(im, im.containsKey("reference") || im.containsKey("until")));
        }
        if (!(m.get("witness") instanceof String w)||!w.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("witness must be a lowercase SHA-256 digest");
        }
        double from=dbl(m.get("from"),"from");
        Leaf.coordinate(from);
        if(from>Leaf.create(config).domainEnd())throw new IllegalArgumentException("Invalid checkpoint coordinate");
        long folded;
        if(m.get("folded") instanceof Long exact){if(exact<0)throw new IllegalArgumentException("folded must be a nonnegative long");folded=exact;}
        else{double numeric=dbl(m.get("folded"),"folded");if(numeric<0||numeric>=0x1p63||numeric!=Math.rint(numeric))throw new IllegalArgumentException("folded must be a nonnegative long");folded=(long)numeric;}
        return new Tail(config,leaf,from,folded,w,open,influences);
    }

    private static Tail coherentTail(Map<String,Object> m){
        Set<String> keys=Set.of("algorithm","from","config","seed","leafSeed","foldedCount","witness","lineage");
        only(m,"coherent tail",keys);all(m,"coherent tail",keys);
        Config config=config(m.get("config"));Seed seed=seedOf(m.get("seed"));
        double from=dbl(m.get("from"),"from"),count=dbl(m.get("foldedCount"),"foldedCount"),id=dbl(m.get("leafSeed"),"leafSeed");
        Leaf.coordinate(from);
        if(from>Leaf.create(config).domainEnd()||count<0||count>9007199254740991d||count!=(long)count||id!=(seed.u32()&0xffffffffL))throw new IllegalArgumentException("Invalid coherent checkpoint metadata");
        if(count==0&&!seed.equals(config.seed())||count>0&&seed.isText())throw new IllegalArgumentException("Invalid coherent checkpoint seed");
        if(!(m.get("witness") instanceof String witness)||!witness.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid coherent checkpoint witness");
        List<Timeline.Entry> open=new ArrayList<>();Seed parent=seed;double latest=1;
        for(Timeline.Snapshot.Fork f:forks(m.get("lineage"))){
            if(f.at()<latest||f.at()+f.handoffWidth()<=from)throw new IllegalArgumentException("Invalid retained checkpoint fork");
            int next=Hash.forkSeed(parent,f.operation(),f.at());open.add(new Timeline.Entry(f.at(),f.operation(),f.handoffWidth(),next));
            parent=Seed.of(next&0xffffffffL);latest=f.at();
        }
        return new Tail(config,seed,from,(long)count,witness,open,List.of(),true);
    }

    /** A sorted copy of a map, for a canonical comparison in tests. */
    static Map<String, Object> sorted(Map<String, Object> m) {
        return new TreeMap<>(m);
    }
}
