package gal.conxugal.application;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * That the deployed configuration really produces the executor a contratos menores import is handed
 * to.
 *
 * <p>Worth its own test because nothing else would notice it missing. The importer is a lazy
 * singleton with no trigger yet, so a name that did not match, or an executor type that produced no
 * bean, would surface on the day the first trigger was wired rather than here — and the failure it
 * would produce then is a context that cannot start.
 */
@MicronautTest(startApplication = false)
class ContratosMenoresImportExecutorIntegrationTest {

  @Inject
  @Named(StartContratosMenoresImport.IMPORT_EXECUTOR)
  Executor imports;

  @Test
  void the_configured_executor_runs_what_an_import_hands_it() throws Exception {
    CompletableFuture<String> ran = new CompletableFuture<>();

    imports.execute(() -> ran.complete(Thread.currentThread().getName()));

    assertThat(ran.get(5, TimeUnit.SECONDS)).isNotNull();
  }

  // Off the thread that asked for the import, which is the whole point of handing it over: a
  // contratos menores import holds its thread for days, and that must never be a request's.
  @Test
  void the_configured_executor_runs_it_on_another_thread() throws Exception {
    CompletableFuture<Thread> ran = new CompletableFuture<>();

    imports.execute(() -> ran.complete(Thread.currentThread()));

    assertThat(ran.get(5, TimeUnit.SECONDS)).isNotSameAs(Thread.currentThread());
  }
}
