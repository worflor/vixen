package dev.woflo.vixen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Seeded composition of source curves through an exactly eligible correlation factor. */
public final class Channels {
    private Channels() {}

    public enum Channel {
        SCORE("score"), UNCONSTRAINED_DEVIATION("unconstrainedDeviation");
        private final String word;
        Channel(String word) { this.word = word; }
        public String word() { return word; }
    }

    public record Options(Channel channel, double tolerance) {
        public Options {
            if (channel == null) throw new IllegalArgumentException("channel is required");
            if (!Double.isFinite(tolerance) || tolerance <= 0) {
                throw new IllegalArgumentException("tolerance must be finite and positive");
            }
        }
        public Options() { this(Channel.SCORE, 1e-10); }
        public static Options defaults() { return new Options(); }
    }

    public record Interval(double start, double end) {
        public Interval {
            if (!Double.isFinite(start) || !Double.isFinite(end) || start > end) {
                throw new IllegalArgumentException("source domain must be an ordered pair of finite numbers");
            }
        }
    }

    public record SourceConfig(long seed, String channel) {}

    public record RootSeed(String type, Object value) {
        public RootSeed {
            if ("text".equals(type)) {
                if (!(value instanceof String)) throw new IllegalArgumentException("Text root seed value required");
            } else if ("uint32".equals(type)) {
                if (!(value instanceof Long n) || n < 0 || n > 0xffffffffL) {
                    throw new IllegalArgumentException("uint32 root seed value required");
                }
            } else throw new IllegalArgumentException("Invalid root seed type");
        }
    }

    /** Immutable replay recipe. Seed values are unsigned uint32 values. */
    public record Recipe(String format, List<List<Double>> matrix, List<List<Double>> factor, double tolerance,
                         String channel, RootSeed rootSeed, List<Long> derivedSeeds, String seedDerivation) {
        public Recipe {
            matrix = immutableMatrix(matrix);
            factor = immutableMatrix(factor);
            derivedSeeds = List.copyOf(derivedSeeds);
        }
    }

    @FunctionalInterface
    public interface CurveFactory {
        Curve create(Seed derivedSeed);
    }

    @FunctionalInterface
    public interface ChannelSampler {
        double[] sample(double point);
    }

    public static CorrelatedChannels createCorrelatedChannels(double[][] matrix, CurveFactory createSource, Seed rootSeed) {
        return createCorrelatedChannels(matrix, createSource, rootSeed, Options.defaults());
    }

    public static CorrelatedChannels createCorrelatedChannels(double[][] matrix, CurveFactory createSource,
                                                               Seed rootSeed, Options options) {
        if (createSource == null) throw new IllegalArgumentException("createSource is required");
        if (rootSeed == null) throw new IllegalArgumentException("rootSeed is required");
        Options selected = options == null ? Options.defaults() : options;
        Correlation.Factorization checked = Correlation.validateCorrelationMatrix(matrix, selected.tolerance());
        int count = checked.matrix().size();
        List<Long> seeds = new ArrayList<>(count);
        Set<Long> unique = new HashSet<>();
        int kind = rootSeed.isText() ? 1 : 2;
        int base = Hash.hash32(rootSeed.u32(), kind, 0x56495845);
        List<Curve> sources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int derived = Hash.hash32(base, i, 0x43484e4c);
            long unsigned = derived & 0xffffffffL;
            if (!unique.add(unsigned)) throw new IllegalArgumentException("Derived source seed collision");
            seeds.add(unsigned);
            Curve source = createSource.create(Seed.of(unsigned));
            if (source == null) throw new IllegalArgumentException("Each source must provide sample(x) and at(x, coordinates)");
            sources.add(source);
        }
        double lo = Double.NEGATIVE_INFINITY;
        double hi = Double.POSITIVE_INFINITY;
        for (Curve source : sources) {
            Interval domain = new Interval(source.domainStart(), source.domainEnd());
            lo = Math.max(lo, domain.start());
            hi = Math.min(hi, domain.end());
        }
        if (lo > hi) throw new IllegalArgumentException("Source curves have no shared domain");
        return new CorrelatedChannels(new Interval(lo, hi), checked, selected.channel(), rootSeed, seeds, sources);
    }

    public static CorrelatedChannels createCorrelatedChannels(double[][] matrix, CurveFactory createSource,
                                                               String rootSeed, Options options) {
        return createCorrelatedChannels(matrix, createSource, Seed.of(rootSeed), options);
    }

    public static CorrelatedChannels createCorrelatedChannels(double[][] matrix, CurveFactory createSource,
                                                               long rootSeed, Options options) {
        return createCorrelatedChannels(matrix, createSource, Seed.of(rootSeed), options);
    }

    /** Convenience factory for independent native Vixen curves with a shared configuration. */
    public static CorrelatedChannels createVixenChannels(double[][] matrix, Config template, Seed rootSeed, Options options) {
        if (template == null) throw new IllegalArgumentException("config is required");
        return createCorrelatedChannels(matrix, seed -> Vixen.create(template.withSeed(seed)), rootSeed, options);
    }

    public static final class CorrelatedChannels {
        private final Interval domain;
        private final Correlation.Factorization checked;
        private final Channel channel;
        private final List<Long> sourceSeeds;
        private final List<SourceConfig> sourceConfigs;
        private final List<Curve> sources;
        private final Recipe recipe;

        private CorrelatedChannels(Interval domain, Correlation.Factorization checked, Channel channel,
                                   Seed rootSeed, List<Long> seeds, List<Curve> sources) {
            this.domain = domain;
            this.checked = checked;
            this.channel = channel;
            this.sourceSeeds = List.copyOf(seeds);
            this.sources = List.copyOf(sources);
            List<SourceConfig> configs = new ArrayList<>(seeds.size());
            for (long seed : seeds) configs.add(new SourceConfig(seed, channel.word()));
            this.sourceConfigs = List.copyOf(configs);
            RootSeed root = rootSeed.isText()
                    ? new RootSeed("text", rootSeed.text())
                    : new RootSeed("uint32", rootSeed.unsigned());
            this.recipe = new Recipe("vixen-correlated-channels/1", checked.matrix(), checked.factor(), checked.tolerance(),
                    channel.word(), root, seeds, "hash32-domain-separated/1");
        }

        public Interval domain() { return domain; }
        public List<List<Double>> matrix() { return checked.matrix(); }
        public List<List<Double>> factor() { return checked.factor(); }
        public double tolerance() { return checked.tolerance(); }
        public double factorError() { return checked.factorError(); }
        public Channel channel() { return channel; }
        public List<Long> sourceSeeds() { return sourceSeeds; }
        public List<SourceConfig> sourceConfigs() { return sourceConfigs; }
        public Recipe recipe() { return recipe; }
        public Recipe serialize() { return recipe; }

        public double[] sample(double x) { return combine(x, null, false); }
        public double[] at(double x, double[] coordinates) { return combine(x, coordinates, true); }

        private double[] combine(double x, double[] coordinates, boolean useCoordinates) {
            if (!Double.isFinite(x) || x < domain.start() || x > domain.end()) {
                throw new IllegalArgumentException("x must lie inside the shared source domain");
            }
            double[] values = new double[sources.size()];
            for (int i = 0; i < sources.size(); i++) {
                Curve source = sources.get(i);
                Sample result = useCoordinates ? source.at(x, coordinates == null ? null : coordinates.clone()) : source.sample(x);
                if (result == null) throw invalidChannel(sourceSeeds.get(i), channel);
                double value = channel == Channel.SCORE ? result.score() : result.unconstrainedDeviation();
                if (!Double.isFinite(value)) throw invalidChannel(sourceSeeds.get(i), channel);
                values[i] = value;
            }
            double[] output = new double[checked.factor().size()];
            for (int i = 0; i < output.length; i++) {
                List<Double> row = checked.factor().get(i);
                double value = 0;
                for (int j = 0; j < row.size(); j++) value += row.get(j) * values[j];
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Correlated channel overflow");
                output[i] = value;
            }
            return output;
        }

        private static IllegalArgumentException invalidChannel(long seed, Channel channel) {
            return new IllegalArgumentException("Source " + seed + " returned an invalid " + channel.word());
        }
    }

    /** Measure ordinary Pearson correlations over caller-supplied reference points. */
    public static List<List<Double>> measureChannelCorrelation(CorrelatedChannels group, double[] points) {
        if (group == null) throw new IllegalArgumentException("Provide a sampler and at least two reference points");
        return measureChannelCorrelation(group::sample, points);
    }

    public static List<List<Double>> measureChannelCorrelation(ChannelSampler sampler, double[] points) {
        if (sampler == null || points == null || points.length < 2) {
            throw new IllegalArgumentException("Provide a sampler and at least two reference points");
        }
        double[][] rows = new double[points.length][];
        int width = -1;
        for (int i = 0; i < points.length; i++) {
            double[] values = sampler.sample(points[i]);
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("Sampler must return a nonempty finite numeric vector");
            }
            if (width < 0) width = values.length;
            else if (values.length != width) throw new IllegalArgumentException("Sampler channel count changed");
            rows[i] = values.clone();
            for (double value : rows[i]) {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Sampler must return a nonempty finite numeric vector");
                }
            }
        }
        int count = rows.length;
        double[] means = new double[width];
        for (double[] row : rows) {
            for (int j = 0; j < width; j++) means[j] += row[j];
        }
        for (int j = 0; j < width; j++) means[j] /= count;
        double[][] centered = new double[count][width];
        double[] sums = new double[width];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < width; j++) {
                double value = rows[i][j] - means[j];
                centered[i][j] = value;
                sums[j] += value * value;
            }
        }
        for (double sum : sums) {
            if (!Double.isFinite(sum) || sum == 0) {
                throw new IllegalArgumentException("Cannot measure correlation with zero or invalid variance");
            }
        }
        List<List<Double>> result = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            List<Double> row = new ArrayList<>(width);
            for (int j = 0; j < width; j++) {
                double cross = 0;
                for (int k = 0; k < count; k++) cross += centered[k][i] * centered[k][j];
                double correlation = cross / StrictMath.sqrt(sums[i] * sums[j]);
                if (!Double.isFinite(correlation) || Math.abs(correlation) > 1 + 1e-12) {
                    throw new IllegalArgumentException("Invalid measured correlation");
                }
                row.add(Math.max(-1.0, Math.min(1.0, correlation)));
            }
            result.add(List.copyOf(row));
        }
        return List.copyOf(result);
    }

    private static List<List<Double>> immutableMatrix(List<List<Double>> matrix) {
        List<List<Double>> rows = new ArrayList<>(matrix.size());
        for (List<Double> row : matrix) rows.add(List.copyOf(row));
        return List.copyOf(rows);
    }
}
