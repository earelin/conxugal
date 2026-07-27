package gal.conxugal.application.scheduling.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import gal.conxugal.domain.organo.ImportOrganos;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class ImportOrganosSchedulerIntegrationTest {

  @Inject ApplicationContext applicationContext;

  @MockBean(ImportOrganos.class)
  ImportOrganos importOrganosMock() {
    return mock(ImportOrganos.class);
  }

  @Test
  void run_is_scheduled_from_the_import_schedule_property_in_europe_madrid() {
    BeanDefinition<ImportOrganosScheduler> definition =
        applicationContext.getBeanDefinition(ImportOrganosScheduler.class);
    ExecutableMethod<ImportOrganosScheduler, ?> run = definition.getRequiredMethod("run");

    assertThat(run.stringValue(Scheduled.class, "cron")).hasValue("0 0 3 * * *");
    assertThat(run.stringValue(Scheduled.class, "zoneId")).hasValue("Europe/Madrid");
  }
}
