package gal.conxugal.application.scheduling.contratosmenores;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The scheduled method while another import holds the guard, simulated by the refusal it produces
 * rather than waited out: the tick logs and ends normally — the refusal reaching Micronaut's
 * scheduled-task handler would be an error report for the system working as intended — and the
 * tick after the guard frees claims.
 */
@MicronautTest
class ContratosMenoresImportSchedulerGuardIntegrationTest {

  @Inject
  ContratosMenoresImportScheduler scheduler;

  @Inject
  StartContratosMenoresImport startImport;

  @MockBean(StartContratosMenoresImport.class)
  StartContratosMenoresImport startContratosMenoresImportMock() {
    return mock(StartContratosMenoresImport.class);
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
  void refused_tick_completes_normally_and_the_next_tick_claims() {
    when(startImport.startAll())
        .thenThrow(new ImportAlreadyRunningException())
        .thenReturn(new ImportRunId(UUID.randomUUID()));

    assertThatCode(scheduler::run).doesNotThrowAnyException();

    scheduler.run();

    verify(startImport, times(2)).startAll();
  }
}
