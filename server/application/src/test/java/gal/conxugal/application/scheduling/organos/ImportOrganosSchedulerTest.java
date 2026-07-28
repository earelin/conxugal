package gal.conxugal.application.scheduling.organos;

import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportOrganosSchedulerTest {

  @Mock private ImportOrganos importOrganos;

  @Test
  void delegates_to_the_import_use_case_on_success() {
    when(importOrganos.run()).thenReturn(ImportOutcome.success(0, 0, 0));
    ImportOrganosScheduler scheduler = new ImportOrganosScheduler(importOrganos);

    scheduler.run();

    // strict stubbing above already fails this test if importOrganos.run() was never called
  }

  @Test
  void delegates_to_the_import_use_case_when_another_run_is_already_in_progress() {
    when(importOrganos.run()).thenReturn(ImportOutcome.alreadyRunning());
    ImportOrganosScheduler scheduler = new ImportOrganosScheduler(importOrganos);

    scheduler.run();

    // strict stubbing above already fails this test if importOrganos.run() was never called
  }

  @Test
  void delegates_to_the_import_use_case_on_failure() {
    when(importOrganos.run()).thenReturn(ImportOutcome.failure());
    ImportOrganosScheduler scheduler = new ImportOrganosScheduler(importOrganos);

    scheduler.run();

    // strict stubbing above already fails this test if importOrganos.run() was never called
  }
}
