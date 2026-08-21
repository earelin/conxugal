package gal.conxugal.application.scheduling.contratosmenores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.domain.importrun.ImportRunId;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * That the tick really arrives without an administrator, on the schedule configuration gave it,
 * and passes straight through: it asks for the whole sweep and nothing narrower. Never {@code
 * startOrgano} — a scheduler that named Órganos would be a fourth trigger able to disagree with
 * the other three about which ones an import covers.
 */
@MicronautTest(startApplication = false)
@Property(name = "conxugal.contratos-menores.import.schedule", value = "*/1 * * * * *")
class ContratosMenoresImportSchedulerFiringIntegrationTest
    extends ContratosMenoresImportPortsTestSupport {

  private static final CountDownLatch START_ALL_INVOKED = new CountDownLatch(1);

  @Inject
  StartContratosMenoresImport startImport;

  @MockBean(StartContratosMenoresImport.class)
  StartContratosMenoresImport startContratosMenoresImportMock() {
    StartContratosMenoresImport mocked = mock(StartContratosMenoresImport.class);
    when(mocked.startAll())
        .thenAnswer(
            invocation -> {
              START_ALL_INVOKED.countDown();
              return new ImportRunId(UUID.randomUUID());
            });
    return mocked;
  }

  @Test
  void fires_on_its_configured_schedule_and_asks_for_the_whole_sweep() throws InterruptedException {
    assertThat(START_ALL_INVOKED.await(5, TimeUnit.SECONDS)).isTrue();

    verify(startImport, never()).startOrgano(any());
  }
}
