package dev.woflo.vixen;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Empirical occupancy calibration for an explicitly declared finite weighted population.
 * Results describe that population or a supplied validation population; they make no guarantee
 * about arbitrary populations or unsupplied source data.
 */
public final class Occupancy {
    public static final String FORMAT = "vixen-occupancy/1";
    public static final int MAX_CANDIDATE_EVALUATIONS = 2 * OccupancyPopulation.MAX_POPULATION;

    private static final BigInteger TWO_TO_1074 = BigInteger.ONE.shiftLeft(1074);
    private static final String POPULATION_BINDING = "sha256-binary64-weighted-multiset/1";
    private static final String EVIDENCE = "measured-on-declared-population-only";
    private static final Set<String> RECIPE_KEYS = Set.of(
            "format", "targetFraction", "outputThreshold", "slope", "scale", "cutoff", "center",
            "totalPopulation", "totalWeight", "achievedFraction", "tieHandling", "populationWitness",
            "populationBinding", "evidence");

    private Occupancy() {}

    /** This API reports measurements only; it does not provide arbitrary-population certification. */
    public enum Evidence { MEASURED }

    /** Calibration controls. Candidate and population limits are explicit and cannot exceed hard limits. */
    public record Options(double targetFraction, double outputThreshold, double slope, double scale,
                          int maxPopulation, int maxCandidateEvaluations) {
        public Options(double targetFraction, double outputThreshold) {
            this(targetFraction, outputThreshold, 1.0, 1.0,
                    OccupancyPopulation.MAX_POPULATION, MAX_CANDIDATE_EVALUATIONS);
        }

        public Options(double targetFraction, double outputThreshold, double slope, double scale) {
            this(targetFraction, outputThreshold, slope, scale,
                    OccupancyPopulation.MAX_POPULATION, MAX_CANDIDATE_EVALUATIONS);
        }

        public Options {
            validateModel(targetFraction, outputThreshold, slope, scale);
            if (targetFraction == 0.0) {
                targetFraction = 0.0;
            }
            if (maxPopulation < 1 || maxPopulation > OccupancyPopulation.MAX_POPULATION) {
                throw new IllegalArgumentException("maxPopulation must be in [1," + OccupancyPopulation.MAX_POPULATION + "]");
            }
            if (maxCandidateEvaluations < 1 || maxCandidateEvaluations > MAX_CANDIDATE_EVALUATIONS) {
                throw new IllegalArgumentException("maxCandidateEvaluations must be in [1," + MAX_CANDIDATE_EVALUATIONS + "]");
            }
        }
    }

    /** A replayable occupancy recipe. A restore without a population retains, but cannot verify, its witness. */
    public static final class Calibration {
        private final double targetFraction;
        private final double outputThreshold;
        private final double slope;
        private final double scale;
        private final double cutoff;
        private final double center;
        private final int totalPopulation;
        private final double totalWeight;
        private final double achievedFraction;
        private final String tieHandling;
        private final String populationWitness;
        private final boolean populationBindingVerified;

        private Calibration(double targetFraction, double outputThreshold, double slope, double scale,
                            double cutoff, double center, int totalPopulation, double totalWeight,
                            double achievedFraction, String tieHandling, String populationWitness,
                            boolean populationBindingVerified) {
            this.targetFraction = targetFraction;
            this.outputThreshold = outputThreshold;
            this.slope = slope;
            this.scale = scale;
            this.cutoff = cutoff;
            this.center = center;
            this.totalPopulation = totalPopulation;
            this.totalWeight = totalWeight;
            this.achievedFraction = achievedFraction;
            this.tieHandling = tieHandling;
            this.populationWitness = populationWitness;
            this.populationBindingVerified = populationBindingVerified;
        }

        public double targetFraction() { return targetFraction; }
        public double outputThreshold() { return outputThreshold; }
        public double slope() { return slope; }
        public double scale() { return scale; }
        public double cutoff() { return cutoff; }
        public double center() { return center; }
        public int totalPopulation() { return totalPopulation; }
        public double totalWeight() { return totalWeight; }
        public double achievedFraction() { return achievedFraction; }
        public double difference() { return achievedFraction - targetFraction; }
        public String tieHandling() { return tieHandling; }
        public String populationWitness() { return populationWitness; }
        public boolean populationBindingVerified() { return populationBindingVerified; }
        public Evidence evidence() { return Evidence.MEASURED; }
        public boolean certified() { return false; }

        /** Evaluates the monotone thresholded logistic transform, preserving the exact cutoff tie. */
        public double evaluate(double deviation) {
            return occupancyValue(outputThreshold, slope, scale, cutoff, deviation);
        }

        /** JSON-safe recipe tree containing only strings, finite numbers, and no implementation objects. */
        public Map<String, Object> serialize() {
            LinkedHashMap<String, Object> recipe = new LinkedHashMap<>();
            recipe.put("format", FORMAT);
            recipe.put("targetFraction", targetFraction);
            recipe.put("outputThreshold", outputThreshold);
            recipe.put("slope", slope);
            recipe.put("scale", scale);
            recipe.put("cutoff", cutoff);
            recipe.put("center", center);
            recipe.put("totalPopulation", totalPopulation);
            recipe.put("totalWeight", totalWeight);
            recipe.put("achievedFraction", achievedFraction);
            recipe.put("tieHandling", tieHandling);
            if(populationWitness!=null){
                recipe.put("populationWitness",populationWitness);
                recipe.put("populationBinding",POPULATION_BINDING);
                recipe.put("evidence",EVIDENCE);
            }
            return Collections.unmodifiableMap(recipe);
        }

        public String snapshotJson() {
            return Vixen.stringifyTree(serialize());
        }

        public String semantics() {
            return "Measured on the declared population only; no arbitrary-population guarantee.";
        }
    }

    /** Empirical report for the supplied finite weighted population. */
    public record Validation(double targetFraction, double achievedFraction, double difference, double absoluteError,
                             int totalPopulation, double totalWeight, int highPopulation, double highWeight,
                             String populationWitness, boolean matchesCalibrationPopulation, Evidence evidence) {
        public boolean certified() { return false; }
        public String semantics() { return "Measured on the supplied population only."; }
    }

    /** Fits the closest attainable weighted fraction on a declared population. */
    public static Calibration calibrate(OccupancyPopulation population, Options options) {
        requirePopulation(population, options == null ? 0 : options.maxPopulation());
        if (options == null) {
            throw new IllegalArgumentException("Calibration options are required");
        }
        GroupTable groups = new GroupTable(population);
        List<Double> candidates = candidateCutoffs(groups.values, options.targetFraction());
        requireCandidateBudget(candidates.size(), options.maxCandidateEvaluations());
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Requested endpoint fraction has no finite representable cutoff");
        }

        BigInteger targetUnits = OccupancyPopulation.units(options.targetFraction());
        BigInteger targetProduct = targetUnits.multiply(population.totalWeightUnits());
        Scored best = null;
        for (double candidate : candidates) {
            Hit hit = groups.includedAtOrAbove(options.outputThreshold(), options.slope(), options.scale(), candidate);
            BigInteger scaledIncluded = hit.weightUnits().shiftLeft(1074);
            BigInteger error = scaledIncluded.subtract(targetProduct).abs();
            boolean aboveTarget = scaledIncluded.compareTo(targetProduct) > 0;
            Scored scored = new Scored(candidate, hit, error, aboveTarget);
            if (best == null || better(scored, best)) {
                best = scored;
            }
        }

        double center = center(best.cutoff(), options.outputThreshold(), options.slope(), options.scale());
        if (!Double.isFinite(center)) {
            throw new IllegalArgumentException("Calibration recipe is not finite for this population and slope");
        }
        String tieHandling = Arrays.binarySearch(groups.values, best.cutoff()) >= 0
                ? "inclusive-at-cutoff" : "values-at-or-above-cutoff";
        double achieved = OccupancyPopulation.ratioAsDouble(best.hit().weightUnits(), population.totalWeightUnits());
        return new Calibration(options.targetFraction(), options.outputThreshold(), options.slope(), options.scale(),
                best.cutoff(), center, population.totalPopulation(), population.totalWeight(), achieved,
                tieHandling, population.witness(), true);
    }

    public static Calibration calibrate(OccupancyPopulation population, double targetFraction, double outputThreshold) {
        return calibrate(population, new Options(targetFraction, outputThreshold));
    }

    public static Calibration calibrate(OccupancyPopulation population, double targetFraction, double outputThreshold,
                                        double slope, double scale) {
        return calibrate(population, new Options(targetFraction, outputThreshold, slope, scale));
    }

    /**
     * Measures a calibration on any supplied population. A changed population is permitted and reported
     * through matchesCalibrationPopulation=false; use validateBound when a matching witness is required.
     */
    public static Validation validate(Calibration calibration, OccupancyPopulation population) {
        if (calibration == null) {
            throw new IllegalArgumentException("A Vixen occupancy calibration is required");
        }
        requirePopulation(population, OccupancyPopulation.MAX_POPULATION);
        GroupTable groups = new GroupTable(population);
        Hit high = groups.includedAtOrAbove(calibration.outputThreshold, calibration.slope, calibration.scale,
                calibration.cutoff);
        double achieved = OccupancyPopulation.ratioAsDouble(high.weightUnits(), population.totalWeightUnits());
        return new Validation(calibration.targetFraction, achieved, achieved - calibration.targetFraction,
                Math.abs(achieved - calibration.targetFraction), population.totalPopulation(), population.totalWeight(),
                high.population(), OccupancyPopulation.unitsAsDouble(high.weightUnits()), population.witness(),
                calibration.populationWitness.equals(population.witness()), Evidence.MEASURED);
    }

    /** Measures only if the supplied population is exactly the calibration's witnessed weighted multiset. */
    public static Validation validateBound(Calibration calibration, OccupancyPopulation declaredPopulation) {
        if (calibration == null || declaredPopulation == null) {
            throw new IllegalArgumentException("Calibration and declared population are required");
        }
        if (!calibration.populationWitness.equals(declaredPopulation.witness())) {
            throw new IllegalArgumentException("Population does not match the calibration witness");
        }
        return validate(calibration, declaredPopulation);
    }

    /** Returns distinct binary64 fractions attainable at the observed-value/nextUp cutoff candidates. */
    public static List<Double> attainableFractions(OccupancyPopulation population, double outputThreshold,
                                                   double slope, double scale) {
        return attainableFractions(population, outputThreshold, slope, scale, MAX_CANDIDATE_EVALUATIONS);
    }

    public static List<Double> attainableFractions(OccupancyPopulation population, double outputThreshold,
                                                   double slope, double scale, int maxCandidateEvaluations) {
        validateModel(0.5, outputThreshold, slope, scale);
        requirePopulation(population, OccupancyPopulation.MAX_POPULATION);
        if (maxCandidateEvaluations < 1 || maxCandidateEvaluations > MAX_CANDIDATE_EVALUATIONS) {
            throw new IllegalArgumentException("maxCandidateEvaluations must be in [1," + MAX_CANDIDATE_EVALUATIONS + "]");
        }
        GroupTable groups = new GroupTable(population);
        List<Double> candidates = candidateCutoffs(groups.values, null);
        requireCandidateBudget(candidates.size(), maxCandidateEvaluations);
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        ArrayList<Double> fractions = new ArrayList<>();
        for (double candidate : candidates) {
            Hit hit = groups.includedAtOrAbove(outputThreshold, slope, scale, candidate);
            double fraction = OccupancyPopulation.ratioAsDouble(hit.weightUnits(), population.totalWeightUnits());
            long bits = Double.doubleToLongBits(fraction);
            if (seen.add(bits)) {
                fractions.add(fraction);
            }
        }
        return List.copyOf(fractions);
    }

    /** Restores a JSON recipe without claiming that its stored population witness has been checked. */
    public static Calibration restore(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON recipe is required");
        }
        return restore(VixenJson.parse(json));
    }

    public static Calibration restore(Object snapshot) {
        if (!(snapshot instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid occupancy recipe");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String text) || !RECIPE_KEYS.contains(text)) {
                throw new IllegalArgumentException("Unknown or invalid occupancy recipe field: " + key);
            }
        }
        Set<String> required=new LinkedHashSet<>(RECIPE_KEYS);
        required.removeAll(Set.of("populationWitness","populationBinding","evidence"));
        if(!map.keySet().containsAll(required))throw new IllegalArgumentException("Occupancy recipe fields are incomplete");
        boolean hasBinding=map.containsKey("populationWitness");
        if(hasBinding!=map.containsKey("populationBinding")||hasBinding!=map.containsKey("evidence"))throw new IllegalArgumentException("Incomplete occupancy population binding");
        if (!FORMAT.equals(stringField(map, "format"))) {
            throw new IllegalArgumentException("Invalid occupancy recipe format");
        }
        if (hasBinding&&(!POPULATION_BINDING.equals(stringField(map, "populationBinding"))
                || !EVIDENCE.equals(stringField(map, "evidence")))) {
            throw new IllegalArgumentException("Invalid occupancy population binding or evidence metadata");
        }

        double target = finiteField(map, "targetFraction");
        double threshold = finiteField(map, "outputThreshold");
        double slope = finiteField(map, "slope");
        double scale = finiteField(map, "scale");
        double cutoff = finiteField(map, "cutoff");
        double center = finiteField(map, "center");
        double totalWeight = finiteField(map, "totalWeight");
        double achieved = finiteField(map, "achievedFraction");
        validateModel(target, threshold, slope, scale);
        if (target == 0.0) {
            target = 0.0;
        }
        int totalPopulation = populationCount(map.get("totalPopulation"));
        if (totalWeight <= 0.0 || achieved < 0.0 || achieved > 1.0) {
            throw new IllegalArgumentException("Invalid occupancy recipe values");
        }
        String tieHandling = stringField(map, "tieHandling");
        if (!tieHandling.equals("inclusive-at-cutoff") && !tieHandling.equals("values-at-or-above-cutoff")) {
            throw new IllegalArgumentException("Invalid occupancy tie handling");
        }
        String witness = hasBinding?stringField(map, "populationWitness"):null;
        if (witness!=null&&!witness.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("populationWitness must be a lowercase SHA-256 digest");
        }
        return new Calibration(target, threshold, slope, scale, cutoff, center, totalPopulation, totalWeight,
                achieved, tieHandling, witness, false);
    }

    /** Restores and verifies the recipe by recalibrating against its declared population. */
    public static Calibration restore(String json, OccupancyPopulation declaredPopulation) {
        if (json == null) {
            throw new IllegalArgumentException("JSON recipe is required");
        }
        return restore(VixenJson.parse(json), declaredPopulation);
    }

    public static Calibration restore(Object snapshot, OccupancyPopulation declaredPopulation) {
        if (declaredPopulation == null) {
            throw new IllegalArgumentException("Declared population is required to verify the binding");
        }
        Calibration restored = restore(snapshot);
        if (restored.populationWitness==null||!restored.populationWitness.equals(declaredPopulation.witness())) {
            throw new IllegalArgumentException("Population does not match the occupancy recipe witness");
        }
        Calibration recomputed = calibrate(declaredPopulation, new Options(restored.targetFraction,
                restored.outputThreshold, restored.slope, restored.scale));
        if (restored.totalPopulation != recomputed.totalPopulation
                || !sameBits(restored.totalWeight, recomputed.totalWeight)
                || !sameBits(restored.cutoff, recomputed.cutoff)
                || !sameBits(restored.achievedFraction, recomputed.achievedFraction)
                || !restored.tieHandling.equals(recomputed.tieHandling)) {
            throw new IllegalArgumentException("Occupancy recipe does not match its declared population");
        }
        return new Calibration(restored.targetFraction, restored.outputThreshold, restored.slope, restored.scale,
                restored.cutoff, restored.center, restored.totalPopulation, restored.totalWeight,
                restored.achievedFraction, restored.tieHandling, restored.populationWitness, true);
    }

    private static boolean better(Scored candidate, Scored incumbent) {
        int error = candidate.errorNumerator().compareTo(incumbent.errorNumerator());
        if (error != 0) {
            return error < 0;
        }
        if (candidate.aboveTarget() != incumbent.aboveTarget()) {
            return !candidate.aboveTarget();
        }
        return candidate.cutoff() < incumbent.cutoff();
    }

    private static List<Double> candidateCutoffs(double[] values, Double targetFraction) {
        ArrayList<Double> candidates = new ArrayList<>(values.length * 2);
        if (targetFraction != null && targetFraction == 1.0) {
            candidates.add(values[0]);
        } else if (targetFraction != null && targetFraction == 0.0) {
            double above = Math.nextUp(values[values.length - 1]);
            if (Double.isFinite(above)) {
                candidates.add(above);
            }
        } else {
            for (double value : values) {
                candidates.add(value);
                double above = Math.nextUp(value);
                if (Double.isFinite(above)) {
                    candidates.add(above);
                }
            }
        }
        return candidates;
    }

    private static void requireCandidateBudget(int candidates, int budget) {
        if (candidates > budget) {
            throw new IllegalArgumentException("Candidate evaluation budget exceeded: " + candidates + " > " + budget);
        }
    }

    private static void requirePopulation(OccupancyPopulation population, int limit) {
        if (population == null) {
            throw new IllegalArgumentException("A declared population is required");
        }
        if (limit > 0 && population.totalPopulation() > limit) {
            throw new IllegalArgumentException("Population row budget exceeded: " + population.totalPopulation() + " > " + limit);
        }
    }

    private static void validateModel(double targetFraction, double outputThreshold, double slope, double scale) {
        if (!Double.isFinite(targetFraction) || targetFraction < 0.0 || targetFraction > 1.0) {
            throw new IllegalArgumentException("targetFraction must be in [0,1]");
        }
        if (!Double.isFinite(outputThreshold) || outputThreshold <= 0.0 || outputThreshold >= 1.0) {
            throw new IllegalArgumentException("outputThreshold must be strictly inside (0,1)");
        }
        double ratio = slope / scale;
        if (!Double.isFinite(slope) || slope <= 0.0 || !Double.isFinite(scale) || scale <= 0.0
                || !Double.isFinite(ratio) || ratio <= 0.0) {
            throw new IllegalArgumentException("slope and scale must have a finite positive ratio");
        }
    }

    private static double center(double cutoff, double threshold, double slope, double scale) {
        double q = 0.5 * (StrictMath.log(threshold) - StrictMath.log1p(-threshold));
        return cutoff - q / (slope / scale);
    }

    private static double occupancyValue(double threshold, double slope, double scale, double cutoff, double deviation) {
        if (!Double.isFinite(deviation)) {
            throw new IllegalArgumentException("deviation must be finite");
        }
        if (deviation == cutoff) {
            return threshold;
        }
        double z = 2.0 * ((slope / scale) * (deviation - cutoff));
        if (z >= 0.0) {
            double e = StrictMath.exp(-z);
            return threshold / (threshold + (1.0 - threshold) * e);
        }
        double e = StrictMath.exp(z);
        return (threshold * e) / (1.0 - threshold + threshold * e);
    }

    private static boolean sameBits(double left, double right) {
        return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }

    private static double finiteField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number) || value instanceof Boolean || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Invalid recipe field: " + key);
        }
        return number.doubleValue();
    }

    private static String stringField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Invalid recipe field: " + key);
        }
        return text;
    }

    private static int populationCount(Object value) {
        if (!(value instanceof Number number) || value instanceof Boolean) {
            throw new IllegalArgumentException("Invalid recipe field: totalPopulation");
        }
        double count=number.doubleValue();
        if(!Double.isFinite(count)||count!=Math.rint(count)||count<1||count>OccupancyPopulation.MAX_POPULATION)throw new IllegalArgumentException("Invalid recipe totalPopulation");
        return (int)count;
    }

    private record Hit(BigInteger weightUnits, int population) {}
    private record Scored(double cutoff, Hit hit, BigInteger errorNumerator, boolean aboveTarget) {}

    private static final class MutableGroup {
        BigInteger weight = BigInteger.ZERO;
        int population;
    }

    /** Groups exact deviation ties and stores suffix totals for exact weighted scoring. */
    private static final class GroupTable {
        private final double[] values;
        private final BigInteger[] suffixWeightUnits;
        private final int[] suffixPopulation;

        private GroupTable(OccupancyPopulation population) {
            TreeMap<Double, MutableGroup> grouped = new TreeMap<>(Double::compare);
            for (OccupancyPopulation.Row row : population.rows()) {
                MutableGroup group = grouped.computeIfAbsent(row.deviation(), ignored -> new MutableGroup());
                group.weight = group.weight.add(OccupancyPopulation.units(row.weight()));
                group.population++;
            }
            values = new double[grouped.size()];
            BigInteger[] weights = new BigInteger[grouped.size()];
            int[] counts = new int[grouped.size()];
            int index = 0;
            for (Map.Entry<Double, MutableGroup> entry : grouped.entrySet()) {
                values[index] = entry.getKey();
                weights[index] = entry.getValue().weight;
                counts[index] = entry.getValue().population;
                index++;
            }
            suffixWeightUnits = new BigInteger[values.length + 1];
            suffixPopulation = new int[values.length + 1];
            suffixWeightUnits[values.length] = BigInteger.ZERO;
            for (int i = values.length - 1; i >= 0; i--) {
                suffixWeightUnits[i] = suffixWeightUnits[i + 1].add(weights[i]);
                suffixPopulation[i] = suffixPopulation[i + 1] + counts[i];
            }
        }

        /** Binary search is valid because the mapped output is monotone; it evaluates rounded outputs at actual values. */
        private Hit includedAtOrAbove(double threshold, double slope, double scale, double cutoff) {
            int lo = 0;
            int hi = values.length;
            while (lo < hi) {
                int mid = lo + (hi - lo) / 2;
                if (occupancyValue(threshold, slope, scale, cutoff, values[mid]) >= threshold) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            return new Hit(suffixWeightUnits[lo], suffixPopulation[lo]);
        }
    }
}
