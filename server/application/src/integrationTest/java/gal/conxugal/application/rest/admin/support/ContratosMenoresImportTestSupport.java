package gal.conxugal.application.rest.admin.support;

import static org.mockito.Mockito.mock;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

/**
 * A stubbed import trigger, and the ports the import beneath it is declared against.
 *
 * <p>The two are one fixture rather than two because the second only exists for the first: mocking
 * the trigger still resolves its real constructor's dependencies, and the whole import hangs off
 * them — the claim, the walk beneath it and the store beneath that all reach adapters wanting a
 * datasource this suite deliberately runs without. Stubbing the ports they are declared against is
 * what keeps a test extending this one a test of the HTTP layer alone.
 *
 * <p>Shared so that a port added to the import's dependency graph is added here once, rather than
 * in every endpoint's suite that happens to ask for a run.
 */
public abstract class ContratosMenoresImportTestSupport extends AuthenticationTestSupport {

  @Inject
  protected StartContratosMenoresImport startImport;

  @MockBean(StartContratosMenoresImport.class)
  protected StartContratosMenoresImport startImportMock() {
    return mock(StartContratosMenoresImport.class);
  }

  @MockBean(ImportRunRepository.class)
  protected ImportRunRepository importRunsMock() {
    return mock(ImportRunRepository.class);
  }

  @MockBean(OrganoRepository.class)
  protected OrganoRepository organosMock() {
    return mock(OrganoRepository.class);
  }

  @MockBean(ContratoMenorRepository.class)
  protected ContratoMenorRepository contratosMock() {
    return mock(ContratoMenorRepository.class);
  }

  @MockBean(OperadorRepository.class)
  protected OperadorRepository operadoresMock() {
    return mock(OperadorRepository.class);
  }

  @MockBean(ContratosMenoresImportStateRepository.class)
  protected ContratosMenoresImportStateRepository importStatesMock() {
    return mock(ContratosMenoresImportStateRepository.class);
  }

  @MockBean(ContratoMenorSource.class)
  protected ContratoMenorSource contratoMenorSourceMock() {
    return mock(ContratoMenorSource.class);
  }
}
