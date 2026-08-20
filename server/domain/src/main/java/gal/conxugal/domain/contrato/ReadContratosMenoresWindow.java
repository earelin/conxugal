package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunRepository;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One window of an Órgano's published contratos menores, paged to exhaustion: the middle of a walk,
 * and the part every walk does identically.
 *
 * <p>What differs between walks is where they start and what ends them — a cursor and the source's
 * own count for an initial import, today and a date floor for an incremental refresh. Between those
 * two ends they do the same thing, in an order that is easy to get wrong, so it is written once
 * here rather than copied.
 *
 * <p>The page is the source's own measured limit, and it is also the batch: one page read, one page
 * upserted and one progress advance are the same beat, which is what keeps the batch tied to the
 * source rather than being a second thing to tune against the guard's abandonment bound.
 *
 * <p><strong>A batch is stored with the operadores its contracts name</strong>, by {@link
 * StoreContratosMenoresBatch} and in one transaction. The published awardee reaches nothing beyond
 * that call: a stored contract keeps neither the name nor the identifier it was published with, so
 * the page read here is the only thing the derivation ever has to read them from.
 *
 * <p><strong>Contracts commit first, the record of them afterwards</strong>, in transactions of
 * their own. A progress write that fails is logged and abandoned: the import wins and the record is
 * what is sacrificed.
 *
 * <p><strong>What this does not own is the cursor.</strong> It takes no
 * {@link gal.conxugal.domain.organo.ContratosMenoresImportStateRepository} and cannot reach one:
 * whatever else a walk records per batch arrives as {@link BatchRecorder}, and the resumption point
 * stays the single walk's that resumes from it.
 */
@Singleton
public class ReadContratosMenoresWindow {

  /** The largest page the source answers, and so the batch a run advances after. */
  static final int PAGE_SIZE = 100;

  private static final Logger LOG = LoggerFactory.getLogger(ReadContratosMenoresWindow.class);

  private final ContratoMenorSource contratoMenorSource;
  private final StoreContratosMenoresBatch batch;
  private final ImportRunRepository importRuns;

  public ReadContratosMenoresWindow(
      ContratoMenorSource contratoMenorSource,
      StoreContratosMenoresBatch batch,
      ImportRunRepository importRuns) {
    this.contratoMenorSource = contratoMenorSource;
    this.batch = batch;
    this.importRuns = importRuns;
  }

  /**
   * Whatever else a walk records once a batch has committed, invoked immediately before the run is
   * advanced and inside the same try/catch. Being inside it is the point: a hook outside would let
   * a transient bookkeeping failure break an import whose contracts are already stored.
   *
   * <p>It is handed the window's own bounds and whether that page exhausted it, as well as the
   * batch's counts, because the conservative cursor rule an initial import follows —
   * {@code lastPage ? windowStart : windowEnd} — cannot be reconstructed from outside this loop.
   * A walk with nothing to record passes a hook that does nothing, which is an honest statement
   * rather than an omission.
   */
  @FunctionalInterface
  public interface BatchRecorder {
    void record(UpsertCounts counts, LocalDate windowStart, LocalDate windowEnd, boolean lastPage);
  }

  /**
   * One window, a page at a time until the source answers a short one — which is what says the
   * window holds nothing further, since it only ever answers a full page while more remains.
   *
   * <p>The guard is asked twice a page, and both asks are interruptions rather than the loop's
   * business. The first means a walk handed a run that is already gone reads nothing at all. The
   * second is the one that does the work the guard exists for: it sits <em>after</em> the batch
   * commits and <em>before</em> the progress write, because the progress write renews the run's own
   * last-advanced stamp — so a walk that asked only before fetching would be reading a liveness it
   * had just written itself, and a stall long enough to lose the guard would be invisible to it.
   * Between those two points the answer is still the one the stalled request left behind.
   *
   * <p><strong>Eligibility is asked once a page, at the very bottom.</strong> That reasoning does
   * not carry over: the mark is written by an administrator rather than by the walk, so nothing
   * here can answer its own question. What matters instead is that the batch is wholly settled
   * first — asked before the progress write, a withdrawn mark would leave the batch's contracts
   * committed while its counts and whatever its walk records were not. Asked only at the top of the
   * loop it would never be evaluated after a window's last page, and the walk would go on to
   * whatever it decides once a window is read out, on a history it was told to stop reading.
   *
   * @throws ContratoMenorSourceUnavailableException if the source becomes unreachable or answers
   *     something unusable — everything stored up to then stands
   */
  public WindowRead read(
      WalkTarget target,
      LocalDate windowStart,
      LocalDate windowEnd,
      BooleanSupplier stillEligible,
      BatchRecorder recordBatch) {
    int added = 0;
    int refreshed = 0;
    int offset = 0;
    long recordsTotal = 0;
    boolean lastPage = false;
    while (!lastPage) {
      if (!importRuns.holdsGuard(target.runId())) {
        return guardLost(target, added, refreshed);
      }
      ContratoMenorSourcePage page =
          contratoMenorSource.fetchPage(
              target.sourceKey(), windowStart, windowEnd, offset, PAGE_SIZE);
      recordsTotal = page.recordsTotal();
      lastPage = page.entries().size() < PAGE_SIZE;
      UpsertCounts counts = batch.store(page.entries(), target.organoId());
      added += counts.added();
      refreshed += counts.refreshed();
      if (!importRuns.holdsGuard(target.runId())) {
        return guardLost(target, added, refreshed);
      }
      recordProgress(target, windowStart, windowEnd, lastPage, counts, recordBatch);
      offset += page.entries().size();
      if (!stillEligible.getAsBoolean()) {
        return noLongerEligible(target, added, refreshed);
      }
    }
    return new WindowRead(added, refreshed, recordsTotal, null);
  }

  /**
   * The window abandoned part-way, because the run behind it stopped holding the guard. What the
   * batches before it stored stands, and the walk's own record of them reaches no further than the
   * last batch's hook — the conservative pair, so a walk that resumes from what it recorded
   * re-reads the window rather than skipping into it, and nothing is stored twice.
   */
  private static WindowRead guardLost(WalkTarget target, int added, int refreshed) {
    LOG.warn(
        "Contratos menores walk of Órgano {} stopped: its run {} no longer holds the import guard,"
            + " so another import may already have claimed it",
        target.organoId(), target.runId());
    return new WindowRead(added, refreshed, 0, StopReason.GUARD_LOST);
  }

  /**
   * The window abandoned at a batch boundary, because the Órgano stopped being one to import. Every
   * batch it read stands, and every batch's hook ran, which is what lets a walk that keeps a
   * resumption point be resumed by a later mark rather than started again.
   */
  private static WindowRead noLongerEligible(WalkTarget target, int added, int refreshed) {
    LOG.info(
        "Contratos menores walk of Órgano {} stopped at a batch boundary: it is no longer active"
            + " and marked for import, and run {} leaves it where it reached",
        target.organoId(), target.runId());
    return new WindowRead(added, refreshed, 0, StopReason.UNMARKED);
  }

  /**
   * The batch boundary: whatever the walk records for itself, then the run advance, each in its own
   * short transaction and all of them after the contracts committed. Neither may break the import,
   * so a failure here is logged and let go — a batch that committed has already happened, and
   * nothing about bookkeeping may undo it.
   */
  private void recordProgress(
      WalkTarget target,
      LocalDate windowStart,
      LocalDate windowEnd,
      boolean lastPage,
      UpsertCounts counts,
      BatchRecorder recordBatch) {
    try {
      recordBatch.record(counts, windowStart, windowEnd, lastPage);
      importRuns.advance(target.runId(), target.organoId(), counts.added(), counts.refreshed());
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores batch for Órgano {} committed but its progress against run {} was not"
              + " recorded; the contracts stand and the walk continues",
          target.organoId(), target.runId(), e);
    }
  }
}
