package gal.conxugal.application.scheduling.contratosmenores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunReport;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The scheduled method wired to the real trigger, with the guard refusing the first tick: a
 * nightly sweep can be turned away by an import it has never heard of, and it neither queues nor
 * retries when it is. Every other test in this package mocks the trigger itself, so none of them
 * can see past the delegation to what a refused tick actually does.
 *
 * <p>The long import holding the guard is simulated by the refusal it produces rather than waited
 * out. What the tick that finally claims goes on to cover is not this test's — here the catalogue
 * has nothing marked, which is the sweep that settles {@code SUCCEEDED} having imported nothing.
 */
@MicronautTest
class ContratosMenoresImportSchedulerGuardIntegrationTest
    extends ContratosMenoresImportPortsTestSupport {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());

  @Inject
  ContratosMenoresImportScheduler scheduler;

  @Inject
  ImportRunRepository importRuns;

  @Test
  void refused_tick_completes_normally_and_the_next_tick_claims() throws Exception {
    CompletableFuture<ImportRunState> settled = new CompletableFuture<>();
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(RUN_ID));
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    when(importRuns.findRun(RUN_ID)).thenReturn(Optional.of(runCovering()));
    doAnswer(invocation -> settled.complete(invocation.getArgument(1, ImportRunState.class)))
        .when(importRuns)
        .complete(eq(RUN_ID), eq(ImportRunState.SUCCEEDED), anyInt(), anyInt());

    assertThatCode(scheduler::run).doesNotThrowAnyException();
    scheduler.run();

    assertThat(settled.get(5, TimeUnit.SECONDS)).isEqualTo(ImportRunState.SUCCEEDED);
  }

  private static ImportRunReport runCovering() {
    return new ImportRunReport(
        RUN_ID,
        Importer.CONTRATOS_MENORES,
        ImportRunState.IN_PROGRESS,
        Instant.EPOCH,
        null,
        0,
        0,
        List.of());
  }
}
