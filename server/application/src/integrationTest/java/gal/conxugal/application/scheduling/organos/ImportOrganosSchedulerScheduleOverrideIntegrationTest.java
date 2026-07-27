package gal.conxugal.application.scheduling.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import gal.conxugal.domain.organo.ImportOrganos;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "conxugal.organos.import.schedule", value = "0 30 4 * * *")
class ImportOrganosSchedulerScheduleOverrideIntegrationTest {

  @Inject ApplicationContext applicationContext;

  @MockBean(ImportOrganos.class)
  ImportOrganos importOrganosMock() {
    return mock(ImportOrganos.class);
  }

  @Test
  void import_schedule_property_can_be_overridden_via_configuration() {
    assertThat(
            applicationContext
                .getEnvironment()
                .getProperty("conxugal.organos.import.schedule", String.class))
        .hasValue("0 30 4 * * *");
  }
}
