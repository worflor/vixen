package dev.woflo.vixen;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public final class Wide {
    public static final String FORMAT = "vixen-scaled-binary64/1";

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO_52 = ONE.shiftLeft(52);
    private static final BigInteger TWO_53 = ONE.shiftLeft(53);
    private static final BigInteger FIFTY_TWO = BigInteger.valueOf(52);
    private static final BigInteger FIFTY_THREE = BigInteger.valueOf(53);
    private static final BigInteger FIFTY_FOUR = BigInteger.valueOf(54);
    private static final Set<String> MAP_KEYS = Set.of("format", "sign", "significand", "exponent2");

    private final BigInteger m;
    private final BigInteger e;

    public Wide(BigInteger m) {
        this(m, ZERO);
    }

    public Wide(BigInteger m, BigInteger e) {
        if (m == null || e == null) {
            throw new IllegalArgumentException("Significand and exponent are required");
        }
        BigInteger[] normalized = normalize(m, e);
        this.m = normalized[0];
        this.e = normalized[1];
    }

    public BigInteger m() {
        return m;
    }

    public BigInteger e() {
        return e;
    }

    public int sign() {
        return m.signum();
    }

    public static Wide fromRatio(BigInteger n,BigInteger d){return fromRatio(n,d,ZERO);}
    public static Wide fromRatio(BigInteger n,BigInteger d,BigInteger power){
        if(n==null||d==null||power==null||d.signum()==0)throw new IllegalArgumentException("Exact ratio with nonzero denominator required");
        if(n.signum()==0)return new Wide(ZERO);
        boolean negative=(n.signum()<0)!=(d.signum()<0);n=n.abs();d=d.abs();int p=n.bitLength()-d.bitLength();
        if(p>=0?n.compareTo(d.shiftLeft(p))<0:n.shiftLeft(-p).compareTo(d)<0)p--;
        int shift=52-p;BigInteger q=shift>=0?roundRatio(n.shiftLeft(shift),d):roundRatio(n,d.shiftLeft(-shift));
        return new Wide(negative?q.negate():q,power.add(BigInteger.valueOf((long)p-52)));
    }

    public static Wide fromDouble(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Finite binary64 input required");
        }
        long bits = Double.doubleToRawLongBits(value);
        int exponentField = (int) ((bits >>> 52) & 0x7ffL);
        long significand = bits & ((1L << 52) - 1L);
        if (exponentField != 0) {
            significand |= 1L << 52;
        }
        BigInteger signedSignificand = BigInteger.valueOf(significand);
        if ((bits & Long.MIN_VALUE) != 0) {
            signedSignificand = signedSignificand.negate();
        }
        long exponent = exponentField == 0 ? -1074L : exponentField - 1075L;
        return new Wide(signedSignificand, BigInteger.valueOf(exponent));
    }

    public Wide neg() {
        return new Wide(m.negate(), e);
    }

    public Wide abs() {
        return m.signum() < 0 ? neg() : this;
    }

    public Wide scale(BigInteger power) {
        if (power == null) {
            throw new IllegalArgumentException("Exact integer scale required");
        }
        return new Wide(m, e.add(power));
    }

    public Wide add(Wide other) {
        Wide b = requireWide(other);
        if (m.signum() == 0) {
            return b;
        }
        if (b.m.signum() == 0) {
            return this;
        }
        Wide high = e.compareTo(b.e) >= 0 ? this : b;
        Wide low = e.compareTo(b.e) >= 0 ? b : this;
        BigInteger gap = high.e.subtract(low.e);
        if (gap.compareTo(FIFTY_FOUR) > 0) {
            return high;
        }
        int shift = gap.intValueExact();
        return new Wide(high.m.shiftLeft(shift).add(low.m), low.e);
    }

    public Wide sub(Wide other) {
        return add(requireWide(other).neg());
    }

    public Wide mul(Wide other) {
        Wide b = requireWide(other);
        return new Wide(m.multiply(b.m), e.add(b.e));
    }

    public Wide div(Wide other) {
        Wide b = requireWide(other);
        if (b.m.signum() == 0) {
            throw new IllegalArgumentException("Nonzero divisor required");
        }
        if (m.signum() == 0) {
            return this;
        }
        BigInteger numerator = m.abs();
        BigInteger denominator = b.m.abs();
        BigInteger shift = numerator.compareTo(denominator) < 0
                ? BigInteger.valueOf(53)
                : FIFTY_TWO;
        BigInteger quotient = roundRatio(numerator.shiftLeft(shift.intValueExact()), denominator);
        boolean negative = (m.signum() < 0) != (b.m.signum() < 0);
        return new Wide(negative ? quotient.negate() : quotient, e.subtract(b.e).subtract(shift));
    }

    public Wide pow(BigInteger power) {
        if (power == null) {
            throw new IllegalArgumentException("Exact integer power required");
        }
        if (power.signum() < 0) {
            return new Wide(ONE).div(pow(power.negate()));
        }
        BigInteger remaining = power;
        Wide base = this;
        Wide result = new Wide(ONE);
        while (remaining.signum() != 0) {
            if (remaining.testBit(0)) {
                result = result.mul(base);
            }
            remaining = remaining.shiftRight(1);
            if (remaining.signum() != 0) {
                base = base.mul(base);
            }
        }
        return result;
    }

    public int cmp(Wide other) {
        Wide b = requireWide(other);
        int signComparison = Integer.compare(sign(), b.sign());
        if (signComparison != 0) {
            return signComparison;
        }
        if (m.signum() == 0) {
            return 0;
        }
        int exponentComparison = e.compareTo(b.e);
        if (exponentComparison != 0) {
            return exponentComparison * sign();
        }
        return Integer.signum(m.compareTo(b.m));
    }

    public Double toDouble() {
        if (m.signum() == 0) {
            return 0.0;
        }
        BigInteger magnitude = m.abs();
        BigInteger binaryExponent = e.add(FIFTY_TWO);
        if (binaryExponent.compareTo(BigInteger.valueOf(1023)) > 0) {
            return null;
        }
        if (binaryExponent.compareTo(BigInteger.valueOf(-1022)) >= 0) {
            int scale = e.intValueExact();
            double value = Math.scalb(magnitude.doubleValue(), scale);
            if (!Double.isFinite(value)) {
                return null;
            }
            return sign() < 0 ? -value : value;
        }

        BigInteger shift = BigInteger.valueOf(-1074).subtract(e);
        if (shift.compareTo(FIFTY_THREE) > 0) {
            return null;
        }
        BigInteger units = roundRatio(magnitude, ONE.shiftLeft(shift.intValueExact()));
        if (units.signum() == 0) {
            return null;
        }
        double value = Math.scalb(units.doubleValue(), -1074);
        return sign() < 0 ? -value : value;
    }

    public Map<String, Object> toMap() {
        if (m.signum() == 0) {
            return Map.of("format", FORMAT, "sign", 0, "significand", 0.0, "exponent2", "0");
        }
        double significand = Math.scalb(m.abs().doubleValue(), -52);
        return Map.of(
                "format", FORMAT,
                "sign", sign(),
                "significand", significand,
                "exponent2", e.add(FIFTY_TWO).toString());
    }

    public static Wide fromMap(Map<String, ?> value) {
        if (value == null || !MAP_KEYS.equals(value.keySet())) {
            throw new IllegalArgumentException("Invalid scaled number");
        }
        Object format = value.get("format");
        Object signValue = value.get("sign");
        Object significandValue = value.get("significand");
        Object exponentValue = value.get("exponent2");
        if (!FORMAT.equals(format) || !(exponentValue instanceof String exponentText)
                || !isCanonicalExponent(exponentText)) {
            throw new IllegalArgumentException("Invalid scaled number");
        }
        int sign = parseSign(signValue);
        double significand = finiteNumber(significandValue);
        if (sign == 0) {
            if (significand != 0.0 || !"0".equals(exponentText)) {
                throw new IllegalArgumentException("Noncanonical scaled zero");
            }
            return new Wide(ZERO);
        }
        if (significand < 1.0 || significand >= 2.0) {
            throw new IllegalArgumentException("Invalid scaled significand");
        }
        BigInteger exponent2 = new BigInteger(exponentText);
        return fromDouble(sign * significand).scale(exponent2);
    }

    private static Wide requireWide(Wide value) {
        if (value == null) {
            throw new IllegalArgumentException("Wide value required");
        }
        return value;
    }

    private static BigInteger[] normalize(BigInteger significand, BigInteger exponent) {
        if (significand.signum() == 0) {
            return new BigInteger[]{ZERO, ZERO};
        }
        boolean negative = significand.signum() < 0;
        BigInteger magnitude = significand.abs();
        int shift = magnitude.bitLength() - 53;
        BigInteger normalizedExponent = exponent;
        if (shift > 0) {
            magnitude = roundRatio(magnitude, ONE.shiftLeft(shift));
            normalizedExponent = normalizedExponent.add(BigInteger.valueOf(shift));
        } else if (shift < 0) {
            magnitude = magnitude.shiftLeft(-shift);
            normalizedExponent = normalizedExponent.add(BigInteger.valueOf(shift));
        }
        if (magnitude.equals(TWO_53)) {
            magnitude = magnitude.shiftRight(1);
            normalizedExponent = normalizedExponent.add(ONE);
        }
        return new BigInteger[]{negative ? magnitude.negate() : magnitude, normalizedExponent};
    }

    private static BigInteger roundRatio(BigInteger numerator, BigInteger denominator) {
        BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(denominator);
        BigInteger quotient = quotientAndRemainder[0];
        int halfComparison = quotientAndRemainder[1].shiftLeft(1).compareTo(denominator);
        if (halfComparison > 0 || (halfComparison == 0 && quotient.testBit(0))) {
            quotient = quotient.add(ONE);
        }
        return quotient;
    }

    private static int parseSign(Object value) {
        double number = finiteNumber(value);
        if (number == -1.0) {
            return -1;
        }
        if (number == 0.0) {
            return 0;
        }
        if (number == 1.0) {
            return 1;
        }
        throw new IllegalArgumentException("Invalid scaled number sign");
    }

    private static double finiteNumber(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Finite numeric value required");
        }
        double result;
        try {
            result = number.doubleValue();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Finite numeric value required", exception);
        }
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Finite numeric value required");
        }
        return result;
    }

    private static boolean isCanonicalExponent(String text) {
        if ("0".equals(text)) {
            return true;
        }
        if (text.isEmpty()) {
            return false;
        }
        int start = text.charAt(0) == '-' ? 1 : 0;
        if (start == text.length() || text.charAt(start) == '0') {
            return false;
        }
        for (int i = start; i < text.length(); i++) {
            char digit = text.charAt(i);
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        return true;
    }
}