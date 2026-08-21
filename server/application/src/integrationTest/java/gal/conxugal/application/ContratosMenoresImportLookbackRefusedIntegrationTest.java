package gal.conxugal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.context.ApplicationContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * That an unusable lookback stops the application coming up, rather than binding quietly and
 * failing at the first import.
 *
 * <p>Two things only this can show, neither of them reachable from a test that constructs the
 * record directly. That the property path really is the one an operator would write — a mistyped
 * prefix would leave the default in place and the context would start. And that the configuration
 * bean is created <em>eagerly</em>: Micronaut builds one the first time something asks for it, and
 * everything that asks for this one hangs off a trigger, so without {@code @Context} a negative
 * margin would boot cleanly and open a permanent hole at the first nightly sweep.
 *
 * <p>Built rather than injected, because a context that must fail to start cannot be the one the
 * test is running in.
 */
class ContratosMenoresImportLookbackRefusedIntegrationTest {

  @Test
  void refuses_to_start_when_the_lookback_would_put_the_floor_ahead_of_the_mark() {
    assertThatThrownBy(() -> startWith("-1d")).hasMessageContaining("lookback must be positive");
  }

  @Test
  void refuses_to_start_when_the_lookback_is_zero() {
    assertThatThrownBy(() -> startWith("0s")).hasMessageContaining("lookback must be positive");
  }

  /** Closed only if it starts, which is the outcome each test asserts against. */
  private static void startWith(String lookback) {
    ApplicationContext.run(Map.of("conxugal.contratos-menores.import.lookback", lookback), "test")
        .close();
  }
}
