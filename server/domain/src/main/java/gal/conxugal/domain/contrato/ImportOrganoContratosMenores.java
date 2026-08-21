package gal.conxugal.domain.contrato;

import gal.conxugal.commons.time.Dates;
import gal.conxugal.domain.contrato.ReadContratosMenoresWindow.BatchRecorder;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads one Órgano's published contratos menores: from the day its history is covered through,
 * backwards in three-month windows, each paged to exhaustion before the walk steps back.
 *
 * <p>One Órgano only. Which Órganos are eligible, the order they are taken in, the run they are
 * covered by and what a failure among them means to the rest are the orchestrator's, and a source
 * failure is deliberately let out of here for it to judge.
 *
 * <p><strong>Newest first</strong>, because an initial import of a large Órgano runs for days: the
 * most-consulted contracts become browsable within hours rather than at the end, and a partial
 * history that grows backwards is one missing its oldest contracts rather than its newest.
 *
 * <p>The window is the source's own measured limit, honoured by construction rather than discovered
 * from its behaviour — an over-wide window answers a bare {@code 500} that nothing can tell from a
 * server fault. Reading one out is {@link ReadContratosMenoresWindow}'s, along with the page that
 * is also the batch; what this class decides is where the windows are.
 *
 * <p><strong>The walk ends when the stored count reaches the count the source reports</strong>, not
 * at the first empty window. For the small Órganos that are most of the catalogue a quarter with no
 * contratos menores is ordinary, and stopping there would mark an Órgano loaded with most of its
 * history unread. That count is live — it grows while a multi-day walk runs — so it is re-read from
 * every response and used as a test evaluated when the walk believes it is done, never as a fixed
 * target. The configured history floor is what bounds a walk whose count never converges, and
 * reaching it leaves the Órgano incomplete.
 *
 * <p><strong>Contracts commit first, the record of them afterwards</strong>, in transactions of
 * their own. A progress write that fails is logged and abandoned: the import wins and the record is
 * what is sacrificed. That leaves the cursor a conservative hint rather than a ledger — a crash
 * between a batch's commit and its cursor write leaves it behind what is stored, and the resumption
 * re-reads that overlap harmlessly, because storing a contract again refreshes it in place.
 *
 * <p><strong>The cursor is this walk's alone.</strong> It is written from the hook handed to the
 * shared window read, which can reach no import state of its own; a resumption is only correct
 * while one writer decides where the walk left off.
 */
@Singleton
public class ImportOrganoContratosMenores {

  /**
   * The widest window the source answers is three months, and this is that bound expressed in the
   * unit a walk stepping <em>backwards</em> can hold it in: 89 days is the shortest three calendar
   * months there is (1 February to 1 May outside a leap year), so a window this wide is within
   * three months of its start whatever the months around it are.
   *
   * <p>Stepping back by months instead would not be, and the asymmetry is silent: {@code
   * minusMonths} clamps to the shorter month and {@code plusMonths} does not undo the clamp, so
   * {@code 2026-05-31} steps back to {@code 2026-02-28}, whose own three months end on
   * {@code 2026-05-28} — a 92-day window the source would have answered, refused as over-wide by
   * arithmetic alone, and refused identically on every resumption because the cursor keeps
   * pointing at it.
   *
   * <p>The step of a walk, not a property of one window, which is why it stays here while the page
   * size moved to the read it bounds.
   */
  static final int WINDOW_DAYS = 89;

  /**
   * The zone the source publishes its dates in. The walk needs it to turn the covered-through
   * instant into the day its first window ends; every date below is a published date, not an
   * instant.
   */
  private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/Madrid");

  private static final Logger LOG = LoggerFactory.getLogger(ImportOrganoContratosMenores.class);

  private final ReadContratosMenoresWindow windows;
  private final ContratoMenorRepository contratos;
  private final ContratosMenoresImportStateRepository importStates;
  private final Clock clock;
  private final ContratosMenoresImportConfiguration configuration;

  public ImportOrganoContratosMenores(
      ReadContratosMenoresWindow windows,
      ContratoMenorRepository contratos,
      ContratosMenoresImportStateRepository importStates,
      Clock clock,
      ContratosMenoresImportConfiguration configuration) {
    this.windows = windows;
    this.contratos = contratos;
    this.importStates = importStates;
    this.clock = clock;
    this.configuration = configuration;
  }

  /**
   * Walks {@code organo}'s history, storing what it reads and recording how far it got against
   * {@code runId}.
   *
   * <p>Not transactional, and that is the point: each batch commits on its own, and the cursor and
   * the run advance after it in transactions of their own. One transaction around the walk would
   * hold a multi-day write open and make a bookkeeping failure roll imported contracts back.
   *
   * @param stillEligible asked at every batch boundary, and the only way this walk can be stopped
   *     from outside: answering false ends it there, keeping everything stored and leaving the
   *     Órgano incomplete so a later mark resumes it rather than restarting it. It is a required
   *     argument rather than one with a default, because a caller able to leave it out is a caller
   *     able to leave a walk running for days after the mark behind it was withdrawn
   * @throws ContratoMenorSourceUnavailableException if the source becomes unreachable or answers
   *     something unusable — everything stored up to then stands, and the cursor is left where the
   *     walk reached
   */
  public ContratosMenoresImportSummary run(
      ImportRunId runId, OrganoDeContratacion organo, BooleanSupplier stillEligible) {
    OrganoId organoId =
        Objects.requireNonNull(organo.id(), "organo must be stored before its contracts are");
    WalkTarget target = new WalkTarget(runId, organoId, organo.sourceKey());
    ContratosMenoresImportState state = stateOf(organoId);
    return walk(target, resumePointOf(state), stillEligible);
  }

  /**
   * The state row as this walk found it, created at T₀ if this is the Órgano's first import. T₀ is
   * stamped once here and carried unchanged across every resumption, which is why a resumption
   * reads the row rather than creating one.
   */
  private ContratosMenoresImportState stateOf(OrganoId organoId) {
    return importStates
        .findByOrganoId(organoId)
        .orElseGet(
            () ->
                importStates.insert(
                    ContratosMenoresImportState.startedAt(organoId, clock.instant())));
  }

  /**
   * Where the first window ends: the cursor a previous attempt left, and only failing that the day
   * T₀ fell on. Never today — measuring a resumption from today would leave everything between the
   * cursor and now outside the walk.
   */
  private static LocalDate resumePointOf(ContratosMenoresImportState state) {
    LocalDate cursorDate = state.cursorDate();
    return cursorDate == null
        ? LocalDate.ofInstant(state.coveredThrough(), SOURCE_ZONE)
        : cursorDate;
  }

  /** Window by window, newest first, until one of the three endings arrives. */
  private ContratosMenoresImportSummary walk(
      WalkTarget target, LocalDate resumePoint, BooleanSupplier stillEligible) {
    LocalDate historyFloor = configuration.historyFloor();
    LocalDate windowEnd = resumePoint;
    int added = 0;
    int refreshed = 0;
    BatchRecorder cursorWrite = cursorWriteFor(target);
    // Strictly after the floor, so a walk resuming from a cursor an earlier one left *at* the
    // floor asks the source for nothing. It has already read every window there is; the day-wide
    // window it would otherwise re-read cannot converge a count that did not converge over the
    // whole history.
    while (windowEnd.isAfter(historyFloor)) {
      LocalDate windowStart = Dates.latest(windowEnd.minusDays(WINDOW_DAYS), historyFloor);
      WindowRead read = windows.read(target, windowStart, windowEnd, stillEligible, cursorWrite);
      added += read.added();
      refreshed += read.refreshed();
      StopReason stoppedBy = read.stoppedBy();
      if (stoppedBy != null) {
        return ContratosMenoresImportSummary.stopped(added, refreshed, stoppedBy);
      }
      if (contratos.countByOrganoId(target.organoId()) >= read.recordsTotal()) {
        // Not best-effort, unlike the progress writes: a completion mark that failed silently
        // would leave a fully loaded Órgano reading as half-loaded for good, and the walk that
        // could correct it is the one being told to stop. Failing the Órgano keeps it resumable.
        importStates.updateState(target.organoId(), ContratosMenoresImportStatus.COMPLETE);
        return ContratosMenoresImportSummary.complete(added, refreshed);
      }
      if (!windowStart.isAfter(historyFloor)) {
        break;
      }
      // The next window ends where this one began. The source was never measured saying whether it
      // counts its end date as within the window, so the two overlap by that day rather than assume
      // — a day dropped per window would surface only as a count that never converges.
      windowEnd = windowStart;
    }
    LOG.warn(
        "Contratos menores walk of Órgano {} reached the configured history floor {} without its"
            + " stored count matching the source's; it is left incomplete and will be resumed",
        target.organoId(), historyFloor);
    return ContratosMenoresImportSummary.incomplete(added, refreshed);
  }

  /**
   * The cursor write this walk hands the shared read, run at every batch boundary and inside the
   * catch that keeps a bookkeeping failure from breaking the import. It is the only thing this walk
   * adds to that read, and the only writer the resumption point has.
   *
   * <p>The cursor is written conservatively. Within a window it stays at the window's end, because
   * a resumption from anywhere inside a window it has not finished paging would skip the rest of
   * it; only the page that exhausts the window moves it back.
   */
  private BatchRecorder cursorWriteFor(WalkTarget target) {
    return (counts, windowStart, windowEnd, lastPage) ->
        importStates.updateCursorDate(target.organoId(), lastPage ? windowStart : windowEnd);
  }
}
