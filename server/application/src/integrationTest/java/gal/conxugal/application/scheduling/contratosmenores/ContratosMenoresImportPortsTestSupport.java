package gal.conxugal.application.scheduling.contratosmenores;

import static org.mockito.Mockito.mock;

import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.test.annotation.MockBean;

/**
 * The ports the import beneath the scheduler is declared against.
 *
 * <p>A scheduler has no question for any of them, and mocks them anyway: a tick that fires
 * resolves the whole import graph — the claim, the walk beneath it and the store beneath that —
 * and those reach adapters wanting a datasource this suite deliberately runs without.
 *
 * <p>Shared so that a port added to the import's dependency graph is added here once rather than
 * in each scheduler test that happens to make a tick arrive.
 */
abstract class ContratosMenoresImportPortsTestSupport {

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
}
