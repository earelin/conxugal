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
  void delegates_to_the_import_use_case_on_each_trigger() {
    when(importOrganos.run()).thenReturn(ImportOutcome.success(0, 0, 0));
    ImportOrganosScheduler scheduler = new ImportOrganosScheduler(importOrganos);

    scheduler.run();

    // strict stubbing above already fails this test if importOrganos.run() was never called
  }
}
