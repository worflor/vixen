package dev.woflo.vixen.test;

import java.util.Objects;

public final class Check {
    private long assertions;
    public long assertions() { return assertions; }
    public void fail(String message) { throw new AssertionError(message); }
    public void that(boolean condition, String message) {
        assertions++;
        if (!condition) fail(message);
    }
    public void equal(Object expected, Object actual, String message) {
        that(Objects.deepEquals(expected, actual), message + ": " + expected + " != " + actual);
    }
    public void equalLong(long expected, long actual, String message) {
        that(expected == actual, message + ": " + expected + " != " + actual);
    }
    public void near(double expected, double actual, double tolerance, String message) {
        that(Double.isFinite(expected) && Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance,
             message + ": " + expected + " != " + actual);
    }
    public void throwsAny(Runnable action, String message) {
        assertions++;
        try { action.run(); }
        catch (RuntimeException expected) { return; }
        fail(message + ": expected a runtime exception");
    }
}
