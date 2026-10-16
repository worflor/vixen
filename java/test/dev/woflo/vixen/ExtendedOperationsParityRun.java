package dev.woflo.vixen;

import java.nio.file.*;
import java.util.*;

public final class ExtendedOperationsParityRun {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    Map<String, Object> fixture =
        (Map<String, Object>)
            VixenJson.parse(Files.readString(Path.of("tests/fixtures/v6/composed.json")));
    int count = 0;
    for (Object item : (List<?>) fixture.get("cases")) {
      Map<String, Object> c = (Map<String, Object>) item;
      Extended.Signal source = (Extended.Signal) Vixen.restore(c.get("snapshot"));
      for (Object row : (List<?>) c.get("samples")) {
        Map<String, Object> s = (Map<String, Object>) row;
        double x = ((Number) s.get("x")).doubleValue();
        ExtendedParityRun.same(
            source.sampleNormalized(x), s.get("expected"), "composed[" + count++ + "]");
      }
      for (Object row : (List<?>) c.get("checkpoints")) {
        Map<String, Object> s = (Map<String, Object>) row;
        Extended.Signal restored = (Extended.Signal) Vixen.restore(s.get("snapshot"));
        double x = ((Number) s.get("x")).doubleValue();
        ExtendedParityRun.same(
            restored.sampleNormalized(x), s.get("expected"), "checkpoint[" + count++ + "]");
      }
    }
    System.out.println(
        "Java extended influence, impulse, redistribution, shared coupling, unary fork and replay"
            + " agree with "
            + count
            + " frozen cross-port cases");
  }
}
