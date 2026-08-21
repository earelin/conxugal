package gal.conxugal.application.scheduling.contratosmenores;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * The zone the nightly hour is read in, which is what puts the sweep after the catalogue import
 * rather than wherever the host's default zone happens to place it.
 *
 * <p>Only asserts {@code zoneId}, not the resolved {@code cron} value: Micronaut resolves a bean
 * method's {@code @Scheduled} placeholder once per JVM and caches it on the compiled annotation
 * metadata, so a cron assertion here could read back whatever value another test's context
 * resolved first. {@link ContratosMenoresImportSchedulerFiringIntegrationTest} proves the cron is
 * genuinely configuration-driven by observing an overridden schedule actually fire.
 */
@MicronautTest
class ContratosMenoresImportSchedulerIntegrationTest {

  @Inject
  ApplicationContext applicationContext;

  @Test
  void run_is_scheduled_in_europe_madrid() {
    BeanDefinition<ContratosMenoresImportScheduler> definition =
        applicationContext.getBeanDefinition(ContratosMenoresImportScheduler.class);
    ExecutableMethod<ContratosMenoresImportScheduler, ?> run = definition.getRequiredMethod("run");

    assertThat(run.stringValue(Scheduled.class, "zoneId")).hasValue("Europe/Madrid");
  }
}
