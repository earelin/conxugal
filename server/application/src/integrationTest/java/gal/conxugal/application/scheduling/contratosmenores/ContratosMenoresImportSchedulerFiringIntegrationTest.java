package gal.conxugal.application.scheduling.contratosmenores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * That the tick really arrives without an administrator, and passes straight through: it asks for
 * the whole sweep and nothing narrower. Never {@code startOrgano} — a scheduler that named Órganos
 * would be a fourth trigger able to disagree with the other three about which ones an import
 * covers.
 */
@MicronautTest
@Property(name = "conxugal.contratos-menores.import.schedule", value = "*/1 * * * * *")
class ContratosMenoresImportSchedulerFiringIntegrationTest {

  private static final CountDownLatch START_ALL_INVOKED = new CountDownLatch(1);

  @Inject
  StartContratosMenoresImport startImport;

  @MockBean(StartContratosMenoresImport.class)
  StartContratosMenoresImport startContratosMenoresImportMock() {
    StartContratosMenoresImport startImport = mock(StartContratosMenoresImport.class);
    when(startImport.startAll())
        .thenAnswer(
            invocation -> {
              START_ALL_INVOKED.countDown();
              return new ImportRunId(UUID.randomUUID());
            });
    return startImport;
  }

  // The mock proxy above still resolves the real constructor's dependencies, and the whole import
  // hangs off them: the claim, the walk beneath it and the store beneath that all reach adapters
  // wanting a datasource this suite deliberately runs without.
  @MockBean(ImportRunRepository.class)
  ImportRunRepository importRunsMock() {
    return mock(ImportRunRepository.class);
  }

  @MockBean(OrganoRepository.class)
  OrganoRepository organosMock() {
    return mock(OrganoRepository.class);
  }

  @MockBean(ContratoMenorRepository.class)
  ContratoMenorRepository contratosMock() {
    return mock(ContratoMenorRepository.class);
  }

  @MockBean(OperadorRepository.class)
  OperadorRepository operadoresMock() {
    return mock(OperadorRepository.class);
  }

  @MockBean(ContratosMenoresImportStateRepository.class)
  ContratosMenoresImportStateRepository importStatesMock() {
    return mock(ContratosMenoresImportStateRepository.class);
  }

  @MockBean(ContratoMenorSource.class)
  ContratoMenorSource contratoMenorSourceMock() {
    return mock(ContratoMenorSource.class);
  }

  @Test
  void fires_on_its_configured_schedule_and_asks_for_the_whole_sweep() throws InterruptedException {
    assertThat(START_ALL_INVOKED.await(5, TimeUnit.SECONDS)).isTrue();

    verify(startImport, never()).startOrgano(any());
  }
}
