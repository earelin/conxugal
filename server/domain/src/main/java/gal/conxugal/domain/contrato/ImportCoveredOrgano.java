package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportMode;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoRepository;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Órgano's turn within a run: whether it is still to be imported at all, which mode its history
 * calls for, the walk itself, and the row that says how it ended.
 *
 * <p>Separate from the run that covers it because the two decide different things, and neither
 * needs the other's reasons. A run decides who is covered, the order they are taken in and what the
 * whole thing amounts to; nothing here knows there are other Órganos at all.
 *
 * <p><strong>An Órgano failing costs only that Órgano.</strong> Everything the step can throw is
 * caught, not only the source being unavailable, and the catch covers reading the catalogue as
 * much as walking it: a momentary failure to read one row must not abandon the Órganos after this
 * one. An Error is deliberately not caught — it says this process is no longer to be trusted with
 * them either.
 *
 * <p><strong>The record never costs the import.</strong> A settlement write that failed and was
 * allowed out would abandon every Órgano after this one over a row, so it is logged and let go.
 */
@Singleton
public class ImportCoveredOrgano {

  private static final Logger LOG = LoggerFactory.getLogger(ImportCoveredOrgano.class);

  private final OrganoRepository organos;
  private final ImportRunRepository importRuns;
  private final ImportOrganoContratosMenores walk;

  public ImportCoveredOrgano(
      OrganoRepository organos,
      ImportRunRepository importRuns,
      ImportOrganoContratosMenores walk) {
    this.organos = organos;
    this.importRuns = importRuns;
    this.walk = walk;
  }

  /**
   * Imports one Órgano the run covers and records how it ended, answering the state its row was
   * settled at.
   *
   * <p>Answers nothing at all when the run stopped being the live one while the Órgano was being
   * walked. That is not an ending the Órgano had, and not one to write down: the run has been
   * claimed by whoever triggered after it went quiet, and its record is theirs.
   */
  public Optional<ImportRunOrganoState> run(ImportRunId runId, OrganoId organoId) {
    Optional<Settlement> settlement = settlementFor(runId, organoId);
    settlement.ifPresent(settled -> settleOrgano(runId, organoId, settled));
    return settlement.map(Settlement::state);
  }

  /**
   * How one Órgano ended, and why if the state does not say it on its own. It exists so the walking
   * and the recording of it stay separate acts — the record is evidence about an import, never a
   * participant in it.
   */
  private record Settlement(ImportRunOrganoState state, @Nullable String reason) {

    /**
     * Recorded on an Órgano whose history is already loaded. It is neither imported nor failed, and
     * reporting it as either would be untrue; naming the mode says why nothing was done.
     */
    private static final String ALREADY_LOADED =
        "Nothing to import: this Órgano's history is loaded, and the %s mode does not exist yet"
            .formatted(ContratosMenoresImportMode.INCREMENTAL);

    /**
     * The two ways a mark can be withdrawn, told apart on the row. Both leave the Órgano exactly as
     * it stands, but only one of them read anything at all, and an administrator looking at a
     * stopped Órgano has no other way to know which happened.
     */
    private static final String UNMARKED_BEFORE_ITS_TURN =
        "Nothing was read: this Órgano stopped being active and marked before the run reached it";

    private static final String UNMARKED_MID_WALK =
        "Stopped at a batch boundary: this Órgano stopped being active and marked while it was"
            + " being imported";

    static Settlement succeeded() {
      return new Settlement(ImportRunOrganoState.SUCCEEDED, null);
    }

    static Settlement unmarkedBeforeItsTurn() {
      return new Settlement(ImportRunOrganoState.STOPPED, UNMARKED_BEFORE_ITS_TURN);
    }

    static Settlement unmarkedMidWalk() {
      return new Settlement(ImportRunOrganoState.STOPPED, UNMARKED_MID_WALK);
    }

    static Settlement alreadyLoaded() {
      return new Settlement(ImportRunOrganoState.SKIPPED, ALREADY_LOADED);
    }

    static Settlement goneFromTheCatalogue() {
      return new Settlement(
          ImportRunOrganoState.FAILED, "This Órgano is no longer in the catalogue");
    }

    static Settlement failed(String reason) {
      return new Settlement(ImportRunOrganoState.FAILED, reason);
    }
  }

  private Optional<Settlement> settlementFor(ImportRunId runId, OrganoId organoId) {
    try {
      return outcomeFor(runId, organoId);
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores import of Órgano {} failed within run {}; what it stored stands and"
              + " the run carries on to the Órganos after it",
          organoId, runId, e);
      return Optional.of(Settlement.failed(reasonOf(e)));
    }
  }

  /**
   * The mode rule decides what happens to an Órgano, not the trigger that arrived — so a mark, a
   * manual sweep and a future scheduler all resume a half-loaded Órgano and all leave a loaded one
   * alone.
   *
   * <p>Eligibility is asked again first, because a sweep reaches its four-hundredth Órgano days
   * after the run enumerated it, and one unmarked in between must have nothing read for it at all.
   */
  private Optional<Settlement> outcomeFor(ImportRunId runId, OrganoId organoId) {
    Optional<OrganoDeContratacion> found = organos.findById(organoId);
    if (found.isEmpty()) {
      return Optional.of(Settlement.goneFromTheCatalogue());
    }
    OrganoDeContratacion organo = found.get();
    if (!organo.eligibleForImport()) {
      return Optional.of(Settlement.unmarkedBeforeItsTurn());
    }
    return switch (ContratosMenoresImportMode.of(organo.importStatus())) {
      case INITIAL, RESUMED -> walked(runId, organo, organoId);
      case INCREMENTAL -> Optional.of(Settlement.alreadyLoaded());
    };
  }

  /**
   * The walk, and what its ending means. A walk stopped because the Órgano was unmarked is an
   * ordinary ending and the run carries on past it; one stopped because the guard went is the run
   * being over, and nothing further is written to it.
   */
  private Optional<Settlement> walked(
      ImportRunId runId, OrganoDeContratacion organo, OrganoId organoId) {
    ContratosMenoresImportSummary summary = walk.run(runId, organo, () -> stillEligible(organoId));
    return switch (summary.stoppedBy()) {
      case null -> Optional.of(Settlement.succeeded());
      case UNMARKED -> Optional.of(Settlement.unmarkedMidWalk());
      case GUARD_LOST -> Optional.empty();
    };
  }

  /** Asked at every batch boundary of the walk, which is what makes an unmark stop it. */
  private boolean stillEligible(OrganoId organoId) {
    return organos.findById(organoId).filter(OrganoDeContratacion::eligibleForImport).isPresent();
  }

  private void settleOrgano(ImportRunId runId, OrganoId organoId, Settlement settlement) {
    try {
      importRuns.finishOrgano(runId, organoId, settlement.state(), settlement.reason());
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores import of Órgano {} ended as {} but run {} does not record it; the"
              + " contracts stand and the run carries on",
          organoId, settlement.state(), runId, e);
    }
  }

  private static String reasonOf(RuntimeException e) {
    String message = e.getMessage();
    return message == null ? e.getClass().getSimpleName() : message;
  }
}
