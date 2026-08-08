package gal.conxugal.application.scheduling.organos;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoRepository;
import gal.conxugal.domain.organo.OrganoSource;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The scheduler's own code is untouched by the move onto the system-wide guard, but what it
 * inherits is not: the nightly catalogue import can now be refused by work it has never heard of.
 * So this drives the real {@code ImportOrganos} through the scheduled method with the guard held,
 * rather than mocking the import and asserting the delegation — the delegation was never the part
 * that changed.
 */
@MicronautTest
class ImportOrganosSchedulerGuardIntegrationTest {

  @Inject
  ImportOrganosScheduler scheduler;

  @Inject
  ImportRunRepository importRuns;

  @Inject
  OrganoSource organoSource;

  @MockBean(ImportRunRepository.class)
  ImportRunRepository importRunsMock() {
    return mock(ImportRunRepository.class);
  }

  @MockBean(OrganoSource.class)
  OrganoSource organoSourceMock() {
    return mock(OrganoSource.class);
  }

  // The catalogue is never reached, so its repository never has to answer; mocking it is what
  // keeps this test off a database it has no question for.
  @MockBean(OrganoRepository.class)
  OrganoRepository organoRepositoryMock() {
    return mock(OrganoRepository.class);
  }

  @Test
  void scheduled_import_reconciles_nothing_while_another_import_holds_the_guard() {
    when(importRuns.claim(Importer.ORGANOS, List.of())).thenReturn(Optional.empty());

    scheduler.run();

    verifyNoInteractions(organoSource);
  }
}
