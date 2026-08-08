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
 * The scheduled method wired to the real {@code ImportOrganos}, with the claim stubbed to refuse:
 * the nightly catalogue import can be turned away by work it has never heard of, and it reconciles
 * nothing when it is. Every other test in this package mocks the import itself, so none of them
 * can see past the delegation to what a refused run actually does.
 *
 * <p>Only that half is assertable here — the scheduled method returns nothing, so the refusal
 * itself is {@code ImportOrganosTest}'s to assert and the durable guard behind it is
 * {@code ImportOrganosGuardIntegrationTest}'s.
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
