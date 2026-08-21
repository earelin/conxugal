package gal.conxugal.domain.contrato;

import static gal.conxugal.domain.contrato.ImportOrganoContratosMenores.SOURCE_ZONE;
import static gal.conxugal.domain.contrato.ImportOrganoContratosMenores.WINDOW_DAYS;

import gal.conxugal.commons.time.Dates;
import gal.conxugal.domain.contrato.ReadContratosMenoresWindow.BatchRecorder;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Re-reads one already-loaded Órgano's recent publications: from today, backwards in the same
 * windows an initial import walks, down to the floor its refresh mark decides.
 *
 * <p>One Órgano only, and the same division of labour the initial walk keeps — which Órganos are
 * eligible, the order they are taken in and what a failure among them means to the rest are the
 * orchestrator's, and a source failure is deliberately let out of here for it to judge.
 *
 * <p>It differs from an initial import at both ends and nowhere in between. It starts at today
 * rather than at a cursor, it ends at a <em>date floor</em> rather than when the stored count
 * reaches the source's, and it settles the refresh mark rather than the {@code COMPLETE} status.
 * Between those two ends the two walks do the identical thing, which is why
 * {@link ReadContratosMenoresWindow} is shared rather than copied. The source is reached only
 * through it, so this mode inherits the outbound client's pace and configures no timeout, no retry
 * and no rate limit of its own.
 *
 * <p><strong>It keeps no resumption state, deliberately.</strong> The cursor exists because an
 * initial import is a multi-day walk whose restart costs days at one request per second; a refresh
 * is a handful of windows whose restart costs seconds. A second writer on the cursor would cost the
 * <em>initial</em> import's resumption its correctness to save those seconds, so the per-batch hook
 * handed to the shared read does nothing at all.
 *
 * <p><strong>The three-state fact is not this walk's to move.</strong> Nothing here touches the
 * status: a refresh leaves the Órgano exactly as loaded as it found it.
 */
@Singleton
public class RefreshOrganoContratosMenores {

  /**
   * The per-batch hook, empty because a refresh records nothing per batch. That is the honest
   * expression of a walk that keeps no resumption point rather than an omission — and it is what
   * keeps the cursor the initial walk's alone, since a shared read that cannot reach the import
   * state cannot write one.
   */
  private static final BatchRecorder RECORDS_NOTHING = (counts, windowStart, windowEnd, last) -> {};

  private final ReadContratosMenoresWindow windows;
  private final ContratosMenoresImportStateRepository importStates;
  private final Clock clock;
  private final ContratosMenoresImportConfiguration configuration;

  public RefreshOrganoContratosMenores(
      ReadContratosMenoresWindow windows,
      ContratosMenoresImportStateRepository importStates,
      Clock clock,
      ContratosMenoresImportConfiguration configuration) {
    this.windows = windows;
    this.importStates = importStates;
    this.clock = clock;
    this.configuration = configuration;
  }

  /**
   * Refreshes {@code organo}'s recent window, storing what it reads and recording how far it got
   * against {@code runId}.
   *
   * <p><strong>The refresh mark is stamped when the walk starts, not when it ends.</strong> A
   * refresh reads the source while publications keep arriving, so a mark taken at the end would put
   * everything published mid-walk below the next run's floor and in reach of nothing. Stamping the
   * start makes consecutive runs overlap by exactly the duration of one refresh, which costs a
   * re-read that storing a contract again makes a no-op.
   *
   * <p><strong>And it is written only when nothing cut the walk off.</strong> A source failure, an
   * unmark or the guard going leaves the mark where it was, so the next run re-reads the same
   * period from the same floor. The write is not best-effort, unlike the progress writes inside the
   * page loop: it follows the completion mark's precedent, so a failure propagates and the
   * orchestrator records the Órgano failed. That costs one duplicated read and no data.
   *
   * @param stillEligible asked at every batch boundary, and the only way this walk can be stopped
   *     from outside: answering false ends it there, keeping everything stored and leaving the
   *     refresh mark unmoved so a later run covers the period it was cut off in
   * @throws ContratoMenorSourceUnavailableException if the source becomes unreachable or answers
   *     something unusable — everything stored up to then stands and the mark is left alone
   */
  public ContratosMenoresRefreshSummary refresh(
      ImportRunId runId, OrganoDeContratacion organo, BooleanSupplier stillEligible) {
    OrganoId organoId =
        Objects.requireNonNull(organo.id(), "organo must be stored before its contracts are");
    Instant refreshedThrough = clock.instant();
    LocalDate floor = floorFor(organoId);
    ContratosMenoresRefreshSummary summary =
        walk(
            new WalkTarget(runId, organoId, organo.sourceKey()),
            LocalDate.ofInstant(refreshedThrough, SOURCE_ZONE),
            floor,
            stillEligible);
    if (summary.stoppedBy() == null) {
      importStates.updateRefreshedThrough(organoId, refreshedThrough);
    }
    return summary;
  }

  /**
   * The day the walk reaches back to: the Órgano's refresh mark if it has one and the instant its
   * history is covered through otherwise, less the lookback margin, on the day the source publishes
   * in.
   *
   * <p>The state row is read rather than created. Only an Órgano whose initial import completed is
   * refreshed at all, and that completion is a write on this very row, so its absence is a
   * contradiction rather than a first import — reported as one instead of quietly inventing a mark.
   */
  private LocalDate floorFor(OrganoId organoId) {
    ContratosMenoresImportState state =
        importStates
            .findByOrganoId(organoId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        ("Órgano %s has no contratos menores import state, so there is nothing to"
                                + " refresh from")
                            .formatted(organoId)));
    return LocalDate.ofInstant(state.incrementalFloor(configuration.lookback()), SOURCE_ZONE);
  }

  /**
   * Window by window, newest first, until one of them covers the floor or something cuts the walk
   * off. For almost every Órgano that is a single window: the floor is a month or so back, and a
   * window is nearly three.
   *
   * <p>Unlike the initial walk this one has no <em>strictly after</em> test to enter on. A refresh
   * always has a window to read — the floor is at most today, and today's publications are exactly
   * what it exists to pick up — so the ending is decided at the bottom of the loop rather than at
   * the top.
   */
  private ContratosMenoresRefreshSummary walk(
      WalkTarget target, LocalDate today, LocalDate floor, BooleanSupplier stillEligible) {
    LocalDate windowEnd = today;
    int added = 0;
    int refreshed = 0;
    while (true) {
      LocalDate windowStart = Dates.latest(windowEnd.minusDays(WINDOW_DAYS), floor);
      WindowRead read =
          windows.read(target, windowStart, windowEnd, stillEligible, RECORDS_NOTHING);
      added += read.added();
      refreshed += read.refreshed();
      StopReason stoppedBy = read.stoppedBy();
      if (stoppedBy != null) {
        return ContratosMenoresRefreshSummary.stopped(added, refreshed, stoppedBy);
      }
      if (!windowStart.isAfter(floor)) {
        return ContratosMenoresRefreshSummary.clean(added, refreshed);
      }
      // The next window ends where this one began, overlapping by that day for the reason the
      // initial walk overlaps: the source was never measured saying whether it counts its end date
      // as within the window, and a day dropped per window would be silent.
      windowEnd = windowStart;
    }
  }
}
