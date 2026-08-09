package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
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
 * <p>The window and the page are the source's own measured limits, honoured by construction rather
 * than discovered from its behaviour — an over-wide window answers a bare {@code 500} that nothing
 * can tell from a server fault. The page is also the batch: one page read, one page upserted and
 * one progress advance are the same beat, which is what keeps the batch tied to the source rather
 * than being a second thing to tune against the guard's abandonment bound.
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
   */
  static final int WINDOW_DAYS = 89;

  /** The largest page the source answers, and so the batch a run advances after. */
  static final int PAGE_SIZE = 100;

  /**
   * The zone the source publishes its dates in. The walk needs it to turn the covered-through
   * instant into the day its first window ends; every date below is a published date, not an
   * instant.
   */
  private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/Madrid");

  private static final Logger LOG = LoggerFactory.getLogger(ImportOrganoContratosMenores.class);

  private final ContratoMenorSource contratoMenorSource;
  private final ContratoMenorRepository contratos;
  private final ContratosMenoresImportStateRepository importStates;
  private final ImportRunRepository importRuns;
  private final Clock clock;
  private final ContratosMenoresImportConfiguration configuration;

  public ImportOrganoContratosMenores(
      ContratoMenorSource contratoMenorSource,
      ContratoMenorRepository contratos,
      ContratosMenoresImportStateRepository importStates,
      ImportRunRepository importRuns,
      Clock clock,
      ContratosMenoresImportConfiguration configuration) {
    this.contratoMenorSource = contratoMenorSource;
    this.contratos = contratos;
    this.importStates = importStates;
    this.importRuns = importRuns;
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
   * @throws ContratoMenorSourceUnavailableException if the source becomes unreachable or answers
   *     something unusable — everything stored up to then stands, and the cursor is left where the
   *     walk reached
   */
  public ContratosMenoresImportSummary run(ImportRunId runId, OrganoDeContratacion organo) {
    OrganoId organoId =
        Objects.requireNonNull(organo.id(), "organo must be stored before its contracts are");
    ContratosMenoresImportState state = stateOf(organoId);
    return walk(runId, organo.sourceKey(), organoId, resumePointOf(state));
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

  /**
   * How one window ended: what it stored, and the count the source reported as it was read.
   *
   * <p>{@code mayContinue} is false when the run stopped holding the guard part-way through the
   * window. The pages already stored stand and are counted here, but nothing beyond that window is
   * this walk's to read — and {@code recordsTotal} then says nothing, because the window it would
   * have been judged against was never read out.
   */
  private record WindowRead(int added, int refreshed, long recordsTotal, boolean mayContinue) {}

  /** Window by window, newest first, until one of the three endings arrives. */
  private ContratosMenoresImportSummary walk(
      ImportRunId runId, String sourceKey, OrganoId organoId, LocalDate resumePoint) {
    LocalDate historyFloor = configuration.historyFloor();
    LocalDate windowEnd = resumePoint;
    int added = 0;
    int refreshed = 0;
    // Strictly after the floor, so a walk resuming from a cursor an earlier one left *at* the
    // floor asks the source for nothing. It has already read every window there is; the day-wide
    // window it would otherwise re-read cannot converge a count that did not converge over the
    // whole history.
    while (windowEnd.isAfter(historyFloor)) {
      LocalDate windowStart = latest(windowEnd.minusDays(WINDOW_DAYS), historyFloor);
      WindowRead read = readWindow(runId, sourceKey, organoId, windowStart, windowEnd);
      added += read.added();
      refreshed += read.refreshed();
      if (!read.mayContinue()) {
        return ContratosMenoresImportSummary.incomplete(added, refreshed);
      }
      if (contratos.countByOrganoId(organoId) >= read.recordsTotal()) {
        // Not best-effort, unlike the progress writes: a completion mark that failed silently
        // would leave a fully loaded Órgano reading as half-loaded for good, and the walk that
        // could correct it is the one being told to stop. Failing the Órgano keeps it resumable.
        importStates.updateState(organoId, ContratosMenoresImportStatus.COMPLETE);
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
        organoId, historyFloor);
    return ContratosMenoresImportSummary.incomplete(added, refreshed);
  }

  /**
   * One window, paged to exhaustion, and the guard asked about twice a batch.
   *
   * <p>The loop condition is the first ask, so a walk handed a run that is already gone reads
   * nothing at all. The second is the one that does the work the guard exists for: it sits
   * <em>after</em> the batch commits and <em>before</em> the progress write, because the progress
   * write renews the run's own last-advanced stamp — so a walk that asked only at the top of the
   * loop would be reading a liveness it had just written itself, and a stall long enough to lose
   * the guard would be invisible to it. Between those two points the answer is still the one the
   * stalled request left behind.
   *
   * <p>Stopping there leaves the batch's contracts committed and the cursor where the previous
   * batch put it, which is the conservative pair: the window is re-read on resumption and nothing
   * is stored twice.
   */
  private WindowRead readWindow(
      ImportRunId runId,
      String sourceKey,
      OrganoId organoId,
      LocalDate windowStart,
      LocalDate windowEnd) {
    int added = 0;
    int refreshed = 0;
    int offset = 0;
    while (importRuns.holdsGuard(runId)) {
      ContratoMenorSourcePage page =
          contratoMenorSource.fetchPage(sourceKey, windowStart, windowEnd, offset, PAGE_SIZE);
      final boolean lastPage = page.entries().size() < PAGE_SIZE;
      UpsertCounts counts = contratos.upsertAll(contratosOf(page, organoId));
      added += counts.added();
      refreshed += counts.refreshed();
      if (!importRuns.holdsGuard(runId)) {
        break;
      }
      recordProgress(runId, organoId, lastPage ? windowStart : windowEnd, counts);
      if (lastPage) {
        return new WindowRead(added, refreshed, page.recordsTotal(), true);
      }
      offset += page.entries().size();
    }
    LOG.warn(
        "Contratos menores walk of Órgano {} stopped: its run {} no longer holds the import guard,"
            + " so another import may already have claimed it",
        organoId, runId);
    return new WindowRead(added, refreshed, 0, false);
  }

  /**
   * The batch boundary: the cursor moves and the run is advanced, each in its own short transaction
   * and both after the contracts committed. Neither may break the import, so a failure here is
   * logged and let go — a batch that committed has already happened, and nothing about bookkeeping
   * may undo it.
   *
   * <p>The cursor is written conservatively. Within a window it stays at the window's end, because
   * a resumption from anywhere inside a window it has not finished paging would skip the rest of
   * it; only the page that exhausts the window moves it back.
   */
  private void recordProgress(
      ImportRunId runId, OrganoId organoId, LocalDate cursorDate, UpsertCounts counts) {
    try {
      importStates.updateCursorDate(organoId, cursorDate);
      importRuns.advance(runId, organoId, counts.added(), counts.refreshed());
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores batch for Órgano {} committed but its progress against run {} was not"
              + " recorded; the contracts stand and the walk continues",
          organoId, runId, e);
    }
  }

  /**
   * The page as contracts of this Órgano. No awardee: the operador a contract was awarded to is
   * derived by the operadores feature, and inventing one here would record an award under nobody.
   */
  private static List<ContratoMenor> contratosOf(ContratoMenorSourcePage page, OrganoId organoId) {
    return page.entries()
        .stream()
        .map(
            entry ->
                new ContratoMenor(
                    entry.sourceId(),
                    organoId,
                    entry.publicationDate(),
                    entry.obxecto(),
                    entry.amount(),
                    entry.duration(),
                    null))
        .toList();
  }

  private static LocalDate latest(LocalDate one, LocalDate other) {
    return one.isAfter(other) ? one : other;
  }
}
