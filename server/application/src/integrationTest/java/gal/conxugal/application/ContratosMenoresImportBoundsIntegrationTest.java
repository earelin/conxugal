package gal.conxugal.application;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.ContratosMenoresImportConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * That the deployed configuration really produces the two bounds a contratos menores walk is held
 * by, neither of which appears in {@code application.yml}.
 *
 * <p>Worth its own test because nothing else would notice them wrong. Both are declared as binding
 * defaults on the record rather than as constants, so a mistyped default — a duration the converter
 * cannot read, a date in the wrong format — surfaces as a context that will not start, and the walk
 * that would have told anyone runs at five in the morning.
 */
@MicronautTest(startApplication = false)
class ContratosMenoresImportBoundsIntegrationTest {

  @Inject
  ContratosMenoresImportConfiguration configuration;

  // Comfortably longer than a plausible administrative correction cycle. Nothing has measured how
  // long after publication the source rectifies an entry, which is why it is configurable at all.
  @Test
  void the_refresh_looks_back_thirty_days_with_no_configuration_present() {
    assertThat(configuration.lookback()).isEqualTo(Duration.ofDays(30));
  }

  @Test
  void the_initial_walk_floors_at_the_start_of_the_published_history() {
    assertThat(configuration.historyFloor()).isEqualTo(LocalDate.of(2018, 1, 1));
  }
}
