package dev.woflo.vixen;

import java.nio.file.*;
import java.util.*;

public final class ExtendedParityRun {
  static int count;

  @SuppressWarnings("unchecked")
  static void same(Object actual, Object expected, String path) {
    if (expected instanceof Map<?, ?> e) {
      if (!(actual instanceof Map<?, ?> a) || !a.keySet().equals(e.keySet()))
        throw new AssertionError(
            path + " keys actual=" + ((Map<?, ?>) actual).keySet() + " expected=" + e.keySet());
      if (Wide.FORMAT.equals(e.get("format"))) {
        Wide av = Wide.fromMap((Map<String, ?>) a), ev = Wide.fromMap((Map<String, ?>) e);
        if (av.cmp(ev) != 0) {
          if (ev.sign() == 0) throw new AssertionError(path + " zero");
          Double error = av.sub(ev).abs().div(ev.abs()).toDouble();
          if (error == null || error > 8e-14)
            throw new AssertionError(path + " wide error " + error);
        }
        return;
      }
      for (var entry : e.entrySet())
        same(a.get(entry.getKey()), entry.getValue(), path + "." + entry.getKey());
      return;
    }
    if (expected instanceof Number e) {
      if (!(actual instanceof Number a)
          || Math.abs(a.doubleValue() - e.doubleValue())
              > Math.max(2e-13, Math.abs(e.doubleValue()) * 8e-14))
        throw new AssertionError(path + ": " + actual + " != " + expected);
      return;
    }
    if (!Objects.equals(actual, expected))
      throw new AssertionError(path + ": " + actual + " != " + expected);
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    Map<String, Object> fixture =
        (Map<String, Object>)
            VixenJson.parse(Files.readString(Path.of("tests/fixtures/v6/native.json")));
    for (Object item : (List<?>) fixture.get("cases")) {
      Map<String, Object> c = (Map<String, Object>) item;
      Extended.Signal curve = (Extended.Signal) Vixen.restore(c.get("snapshot"));
      for (Object row : (List<?>) c.get("samples")) {
        Map<String, Object> sample = (Map<String, Object>) row;
        double x = ((Number) sample.get("x")).doubleValue();
        Map<String, Object> p;
        if (sample.containsKey("h")) {
          List<?> h = (List<?>) sample.get("h");
          double[] vector = new double[16];
          for (int i = 0; i < 16; i++) vector[i] = ((Number) h.get(i)).doubleValue();
          p = curve.atNormalized(x, vector);
        } else p = curve.sampleNormalized(x);
        same(p, sample.get("expected"), "sample[" + count++ + "]");
      }
      for (Object row : (List<?>) c.get("checkpoints")) {
        Map<String, Object> cp = (Map<String, Object>) row;
        Extended.Signal replay = (Extended.Signal) Vixen.restore(cp.get("snapshot"));
        double x = ((Number) cp.get("x")).doubleValue();
        same(replay.sampleNormalized(x), cp.get("expected"), "checkpoint[" + count++ + "]");
      }
    }
    System.out.println(
        "Java extended cross-port conformance: "
            + count
            + " frozen native samples/checkpoints passed");
  }
}
