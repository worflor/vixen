package dev.woflo.vixen;

import java.math.BigInteger;
import java.util.Map;

public final class WideRun {
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO_52 = ONE.shiftLeft(52);
    private static final BigInteger TWO_53 = ONE.shiftLeft(53);
    private static final BigInteger TWO_54 = ONE.shiftLeft(54);

    private WideRun() {
    }

    public static void main(String[] args) {
        Wide oneAndHalf = Wide.fromDouble(1.5);
        check(oneAndHalf.m().equals(TWO_52.multiply(BigInteger.valueOf(3)).shiftRight(1)), "1.5 significand");
        check(oneAndHalf.e().equals(BigInteger.valueOf(-52)), "1.5 exponent");
        check(oneAndHalf.sign() == 1 && oneAndHalf.toDouble() == 1.5, "1.5 value");
        Map<String, Object> oneAndHalfMap = Map.of(
                "format", "vixen-scaled-binary64/1",
                "sign", 1,
                "significand", 1.5,
                "exponent2", "0");
        check(oneAndHalf.toMap().equals(oneAndHalfMap), "1.5 serialization");
        check(same(Wide.fromMap(oneAndHalfMap), oneAndHalf), "1.5 map round trip");

        Wide minimum = Wide.fromDouble(Double.MIN_VALUE);
        check(minimum.toMap().get("exponent2").equals("-1074"), "minimum subnormal serialization");
        check(minimum.toDouble() == Double.MIN_VALUE, "minimum subnormal conversion");
        check(Wide.fromDouble(Double.MAX_VALUE).toDouble() == Double.MAX_VALUE, "maximum finite conversion");
        check(Wide.fromDouble(-0.0).m().signum() == 0 && Wide.fromDouble(-0.0).e().signum() == 0,
                "canonical zero");
        check(Double.doubleToRawLongBits(new Wide(ZERO).toDouble()) == 0L, "zero conversion");

        Wide lowerTie = new Wide(TWO_53.add(ONE), ZERO);
        check(lowerTie.m().equals(TWO_52) && lowerTie.e().equals(ONE), "lower-neighbour ties-to-even");
        Wide justBelowPower = new Wide(TWO_53.subtract(ONE), ZERO);
        check(justBelowPower.m().equals(TWO_53.subtract(ONE)) && justBelowPower.e().equals(ZERO),
                "value just below a power of two");
        Wide upperTie = new Wide(TWO_54.subtract(ONE), ZERO);
        check(upperTie.m().equals(TWO_52) && upperTie.e().equals(BigInteger.valueOf(2)),
                "upper-neighbour ties-to-even at a power of two");

        Wide power = new Wide(TWO_52, ZERO);
        Wide lowerNeighbor = new Wide(TWO_53.subtract(ONE), BigInteger.valueOf(-54));
        Wide gap54Result = power.sub(lowerNeighbor);
        check(gap54Result.m().equals(TWO_53.subtract(ONE))
                        && gap54Result.e().equals(BigInteger.valueOf(-1)),
                "gap-54 subtraction rounds to the lower neighbour");
        Wide gap55Result = power.sub(new Wide(TWO_53.subtract(ONE), BigInteger.valueOf(-55)));
        check(same(gap55Result, power), "gap-55 addition shortcut");

        Wide arithmetic = Wide.fromDouble(3.0).div(Wide.fromDouble(2.0));
        check(arithmetic.toDouble() == 1.5, "division");
        check(Wide.fromDouble(1.5).mul(Wide.fromDouble(1.5)).toDouble() == 2.25, "multiplication");
        check(Wide.fromDouble(1.5).pow(BigInteger.valueOf(2)).toDouble() == 2.25, "power");
        check(Wide.fromDouble(2.0).cmp(Wide.fromDouble(1.5)) > 0, "comparison");

        Wide halfMinimum = new Wide(TWO_52, BigInteger.valueOf(-1127));
        check(halfMinimum.toDouble() == null, "underflow returns null");
        Wide beyondMaximum = new Wide(TWO_52, BigInteger.valueOf(972));
        check(beyondMaximum.toDouble() == null, "overflow returns null");

        BigInteger hugeExponent = BigInteger.TEN.pow(100);
        Wide huge = new Wide(TWO_52, hugeExponent);
        Wide muchSmaller = new Wide(TWO_52, hugeExponent.subtract(BigInteger.valueOf(55)));
        check(same(huge.add(muchSmaller), huge), "huge exponent addition");
        check(huge.add(huge.neg()).m().signum() == 0 && huge.add(huge.neg()).e().signum() == 0,
                "huge exponent cancellation");

        expectInvalid(() -> new Wide((BigInteger) null), "null significand");
        expectInvalid(() -> new Wide(ONE, null), "null exponent");
        expectInvalid(() -> Wide.fromDouble(Double.NaN), "nonfinite binary64");
        expectInvalid(() -> Wide.fromDouble(Double.POSITIVE_INFINITY), "infinite binary64");
        expectInvalid(() -> Wide.fromDouble(1.0).div(new Wide(ZERO)), "division by zero");
        expectInvalid(() -> Wide.fromMap(Map.of("format", "vixen-scaled-binary64/1", "sign", 1,
                "significand", 1.0)), "missing map key");
        expectInvalid(() -> Wide.fromMap(Map.of("format", "vixen-scaled-binary64/1", "sign", 1,
                "significand", 1.0, "exponent2", "0", "extra", 1)), "extra map key");
        expectInvalid(() -> Wide.fromMap(Map.of("format", "vixen-scaled-binary64/1", "sign", 1,
                "significand", 1.0, "exponent2", "00")), "noncanonical exponent");
        expectInvalid(() -> Wide.fromMap(Map.of("format", "vixen-scaled-binary64/1", "sign", 1,
                "significand", 2.0, "exponent2", "0")), "noncanonical significand");
        expectInvalid(() -> Wide.fromMap(Map.of("format", "vixen-scaled-binary64/1", "sign", 0,
                "significand", 0.0, "exponent2", "1")), "noncanonical zero");
        expectInvalid(() -> Wide.fromMap(Map.of("format", "vixen-scaled-binary64/1", "sign", true,
                "significand", 1.0, "exponent2", "0")), "boolean sign");
        expectInvalid(() -> Wide.fromMap(Map.of("format", "vixen-scaled-binary64/1", "sign", 1,
                "significand", Double.NaN, "exponent2", "0")), "nonfinite significand");

        System.out.println("Wide arithmetic checks passed");
    }

    private static boolean same(Wide left, Wide right) {
        return left.m().equals(right.m()) && left.e().equals(right.e());
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectInvalid(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + label);
    }
}