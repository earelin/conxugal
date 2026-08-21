package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportMode;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
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

  /** Long enough for any message worth reading, short enough that no row becomes a log dump. */
  private static final int LONGEST_REASON = 500;

  private static final Logger LOG = LoggerFactory.getLogger(ImportCoveredOrgano.class);

  private final OrganoRepository organos;
  private final ImportRunRepository importRuns;
  private final ImportOrganoContratosMenores walk;
  private final RefreshOrganoContratosMenores refresh;

  public ImportCoveredOrgano(
      OrganoRepository organos,
      ImportRunRepository importRuns,
      ImportOrganoContratosMenores walk,
      RefreshOrganoContratosMenores refresh) {
    this.organos = organos;
    this.importRuns = importRuns;
    this.walk = walk;
    this.refresh = refresh;
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
     * The two ways a mark can be withdrawn, told apart on the row. Both leave the Órgano exactly as
     * it stands, but only one of them read anything at all, and an administrator looking at a
     * stopped Órgano has no other way to know which happened.
     */
    private static final String UNMARKED_BEFORE_ITS_TURN =
        "Nothing was read: this Órgano stopped being active and marked before the run reached it";

    private static final String UNMARKED_MID_WALK =
        "Stopped at a batch boundary: this Órgano stopped being active and marked while it was"
            + " being imported";

    private static final String REACHED_THE_HISTORY_FLOOR =
        "Read every window down to the configured history floor without the stored count matching"
            + " the source's; this Órgano is imported as far as it can be and stays incomplete";

    /**
     * The Órgano read its history out: its stored count reached the one the source reports. One of
     * the two endings that leave nothing more to say, and so one of the two carrying no reason.
     */
    static Settlement readItsHistoryOut() {
      return new Settlement(ImportRunOrganoState.SUCCEEDED, null);
    }

    /**
     * The other one: a refresh that read every window down to its floor. An already-loaded Órgano
     * is now current, which is the whole of what happened — and a reason here would be a sentence
     * an administrator reads on every Órgano of every recurring run.
     */
    static Settlement refreshedItsRecentWindow() {
      return new Settlement(ImportRunOrganoState.SUCCEEDED, null);
    }

    /**
     * A walk that read every window it had and still fell short. Not a failure — it did all the
     * work there was — but not a loaded Órgano either, and without a reason it would read on the
     * row exactly like one that finished, on this run and on every run after it.
     */
    static Settlement reachedTheHistoryFloor() {
      return new Settlement(ImportRunOrganoState.SUCCEEDED, REACHED_THE_HISTORY_FLOOR);
    }

    static Settlement unmarkedBeforeItsTurn() {
      return new Settlement(ImportRunOrganoState.STOPPED, UNMARKED_BEFORE_ITS_TURN);
    }

    static Settlement unmarkedMidWalk() {
      return new Settlement(ImportRunOrganoState.STOPPED, UNMARKED_MID_WALK);
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
   * manual sweep and a future scheduler all resume a half-loaded Órgano and all refresh a loaded
   * one.
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
      case INCREMENTAL -> refreshed(runId, organo, organoId);
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
      case null -> Optional.of(endedOnItsOwnTerms(summary));
      case UNMARKED -> Optional.of(Settlement.unmarkedMidWalk());
      case GUARD_LOST -> Optional.empty();
    };
  }

  /**
   * The refresh, and what its ending means. Both interruptions mean to it exactly what they mean to
   * a walk, and it is handed the same eligibility check, so an unmark stops it at a batch boundary
   * just the same. Only its clean ending differs: a refresh reads down to its own floor and reaches
   * no history floor, so it settles with nothing left to explain.
   */
  private Optional<Settlement> refreshed(
      ImportRunId runId, OrganoDeContratacion organo, OrganoId organoId) {
    ContratosMenoresRefreshSummary summary =
        refresh.refresh(runId, organo, () -> stillEligible(organoId));
    return switch (summary.stoppedBy()) {
      case null -> Optional.of(Settlement.refreshedItsRecentWindow());
      case UNMARKED -> Optional.of(Settlement.unmarkedMidWalk());
      case GUARD_LOST -> Optional.empty();
    };
  }

  /**
   * A walk nothing interrupted, which is still two endings: the history read out, or every window
   * read down to the floor without the count converging. Both succeeded — neither failed and both
   * did all the work there was — and only the reason tells them apart afterwards.
   */
  private static Settlement endedOnItsOwnTerms(ContratosMenoresImportSummary summary) {
    return summary.status() == ContratosMenoresImportStatus.COMPLETE
        ? Settlement.readItsHistoryOut()
        : Settlement.reachedTheHistoryFloor();
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

  /**
   * What a failure is recorded as. The message of an arbitrary runtime exception is whatever threw
   * it felt like saying — a statement fragment, a URL, a stack of causes — and this one is stored
   * on the row and served to an administrator, so it is capped rather than trusted to be short.
   */
  private static String reasonOf(RuntimeException e) {
    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    return message.length() <= LONGEST_REASON
        ? message
        : message.substring(0, LONGEST_REASON) + "…";
  }
}
