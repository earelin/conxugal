package gal.conxugal.application.scheduling.contratosmenores;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContratosMenoresImportSchedulerTest {

  @Mock private StartContratosMenoresImport startImport;

  @Test
  void asks_for_the_sweep_of_every_eligible_organo() {
    when(startImport.startAll()).thenReturn(new ImportRunId(UUID.randomUUID()));
    ContratosMenoresImportScheduler scheduler = new ContratosMenoresImportScheduler(startImport);

    assertThatCode(scheduler::run).doesNotThrowAnyException();
  }

  // A refusal reaching Micronaut's scheduled-task handler would be an error report for the guard
  // doing its job, so the tick swallows it and the next one asks again.
  @Test
  void ends_normally_when_another_import_holds_the_guard() {
    when(startImport.startAll()).thenThrow(new ImportAlreadyRunningException());
    ContratosMenoresImportScheduler scheduler = new ContratosMenoresImportScheduler(startImport);

    assertThatCode(scheduler::run).doesNotThrowAnyException();
  }
}
