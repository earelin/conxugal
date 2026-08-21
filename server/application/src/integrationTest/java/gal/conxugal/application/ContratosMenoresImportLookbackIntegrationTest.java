package gal.conxugal.application;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.ContratosMenoresImportConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * That the deployed configuration really produces the margin an incremental refresh reaches back
 * by, which appears in no {@code application.yml}.
 *
 * <p>Worth its own test because nothing else would notice it wrong. The default is a string the
 * duration converter has to parse, so a unit it does not accept produces a context that will not
 * start — and the walk that would have said so runs at five in the morning.
 */
@MicronautTest(startApplication = false)
class ContratosMenoresImportLookbackIntegrationTest {

  @Inject
  ContratosMenoresImportConfiguration configuration;

  // Comfortably longer than a plausible administrative correction cycle. Nothing has measured how
  // long after publication the source rectifies an entry, which is why it is configurable at all.
  @Test
  void the_refresh_looks_back_thirty_days_with_no_configuration_present() {
    assertThat(configuration.lookback()).isEqualTo(Duration.ofDays(30));
  }
}
