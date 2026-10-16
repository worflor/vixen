package dev.woflo.vixen.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Suite {
    private record Case(String name, Consumer<Check> test) {}
    private final List<Case> cases = new ArrayList<>();
    private final String name;
    public Suite(String name) { this.name = name; }
    public Suite add(String name, Consumer<Check> test) {
        cases.add(new Case(name, test));
        return this;
    }
    public int report() {
        int failed = 0;
        long assertions = 0;
        for (Case item : cases) {
            Check check = new Check();
            try {
                item.test.accept(check);
                System.out.println("PASS " + item.name);
            } catch (Exception | AssertionError failure) {
                failed++;
                System.err.println("FAIL " + item.name);
                failure.printStackTrace();
            }
            assertions += check.assertions();
        }
        System.out.println(name + ": " + cases.size() + " cases, " + assertions + " assertions, " + failed + " failures");
        return failed == 0 ? 0 : 1;
    }
}
