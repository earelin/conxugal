package gal.conxugal.application;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.ContratosMenoresImportConfiguration;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The lookback is a guess about the source rather than a fact about it, so the property that moves
 * it is part of what this feature ships — the first measurement of how long the source takes to
 * rectify an entry has to be actionable without a release.
 *
 * <p>Its own class because the deployed default and an override cannot both be observed in one
 * context, and the default is the half {@link ContratosMenoresImportBoundsIntegrationTest} pins.
 */
@MicronautTest(startApplication = false)
@Property(name = "conxugal.contratos-menores.import.lookback", value = "45d")
class ContratosMenoresImportLookbackOverrideIntegrationTest {

  @Inject
  ContratosMenoresImportConfiguration configuration;

  @Test
  void the_refresh_looks_back_as_far_as_it_is_configured_to() {
    assertThat(configuration.lookback()).isEqualTo(Duration.ofDays(45));
  }
}
