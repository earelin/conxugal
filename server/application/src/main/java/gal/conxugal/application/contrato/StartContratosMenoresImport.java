package gal.conxugal.application.contrato;

import gal.conxugal.domain.contrato.ClaimContratosMenoresImport;
import gal.conxugal.domain.contrato.ExecuteContratosMenoresImport;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a contratos menores import and answers which run is doing it — one call per kind of
 * import, so nothing that triggers one has to remember to finish it.
 *
 * <p><strong>This is where the import stops being synchronous.</strong> Claiming answers in
 * milliseconds and walking runs for days, and they are deliberately two use cases so that neither
 * the claim's answer nor the walk's duration is imposed on the other. Pairing them —
 * and choosing the thread the long half runs on — is a driving-side decision, which is why it is
 * made here rather than in the domain: a trigger is what needs an answer now, and a trigger is
 * what knows there is somewhere else to put the work.
 *
 * <p><strong>Refusals travel as they arrive.</strong> Claiming throws when an import already holds
 * the guard, when the named Órgano is not eligible, or when it is not there at all, and nothing
 * here catches any of it: each has a handler that turns it into the response it deserves, and a
 * caller that wants one of them to mean something else — the import mark, which applies whether or
 * not an import starts — is better placed to say so than this is.
 */
@Singleton
public class StartContratosMenoresImport {

  /**
   * The executor the walking is handed to, qualified because it must not be the one serving
   * requests: a single import occupies its thread for days.
   */
  public static final String IMPORT_EXECUTOR = "contratos-menores-import";

  private static final Logger LOG = LoggerFactory.getLogger(StartContratosMenoresImport.class);

  private final ClaimContratosMenoresImport claim;
  private final ExecuteContratosMenoresImport execute;
  private final ImportRunRepository importRuns;
  private final Executor executor;

  public StartContratosMenoresImport(
      ClaimContratosMenoresImport claim,
      ExecuteContratosMenoresImport execute,
      ImportRunRepository importRuns,
      @Named(IMPORT_EXECUTOR) Executor executor) {
    this.claim = claim;
    this.execute = execute;
    this.importRuns = importRuns;
    this.executor = executor;
  }

  /**
   * Starts an import of every eligible Órgano.
   *
   * @throws ImportAlreadyRunningException if another import holds the guard
   */
  public ImportRunId startAll() {
    return start(claim.claimAll());
  }

  /**
   * Starts an import of one named Órgano.
   *
   * @throws OrganoNotFoundException if no Órgano has this identity
   * @throws OrganoNotEligibleForImportException if it is not active in the catalogue and marked
   * @throws ImportAlreadyRunningException if another import holds the guard
   */
  public ImportRunId startOrgano(OrganoId organoId) {
    return start(claim.claimOrgano(organoId));
  }

  /**
   * Hands the walking over and answers the identity the claim produced.
   *
   * <p>A submission the executor refuses settles the run before it propagates. The run is already
   * claimed by then, and left as it stands it would hold the system-wide guard until the
   * abandonment bound passed — refusing every import in the meantime for work no thread was ever
   * going to do.
   */
  private ImportRunId start(ImportRunId runId) {
    try {
      executor.execute(() -> walk(runId));
    } catch (RuntimeException e) {
      settleUnstarted(runId);
      throw e;
    }
    return runId;
  }

  /**
   * The walking, on the executor's thread with nothing above it to catch anything. Whatever comes
   * out of the run has already been recorded by the time it reaches here, so this logs and stops
   * rather than letting it reach a default handler that says nothing about which import it was.
   */
  private void walk(ImportRunId runId) {
    try {
      execute.execute(runId);
    } catch (RuntimeException e) {
      LOG.error("Contratos menores run {} ended in an unhandled failure", runId, e);
    }
  }

  private void settleUnstarted(ImportRunId runId) {
    try {
      importRuns.complete(runId, ImportRunState.FAILED, 0, 0);
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores run {} was claimed but never started, and could not be settled"
              + " either; the guard stays held until the abandonment bound passes",
          runId, e);
    }
  }
}
