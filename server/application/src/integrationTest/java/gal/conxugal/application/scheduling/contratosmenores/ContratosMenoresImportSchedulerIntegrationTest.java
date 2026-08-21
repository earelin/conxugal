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
 * The nightly hour and the zone it is read in — the two halves of <em>after the catalogue
 * import</em>. Both would be safe under the guard, but one of two triggers firing at the same
 * instant always loses, and losing nightly is not a schedule.
 *
 * <p>The cron is read off the environment rather than the resolved {@code @Scheduled} metadata,
 * which is the annotation's own placeholder and not the shipped value.
 * {@link ContratosMenoresImportSchedulerFiringIntegrationTest} is what proves the annotation
 * genuinely drives the schedule, by overriding it and watching a tick arrive.
 */
@MicronautTest(startApplication = false)
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

  @Test
  void the_shipped_schedule_is_daily_at_five() {
    assertThat(
            applicationContext.getProperty(
                "conxugal.contratos-menores.import.schedule", String.class))
        .hasValue("0 0 5 * * *");
  }
}
