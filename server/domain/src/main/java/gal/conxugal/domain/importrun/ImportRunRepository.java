package gal.conxugal.domain.importrun;

import gal.conxugal.domain.organo.OrganoId;
import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Port for import runs, and for the guard that admits one of them at a time. Implemented by the
 * {@code infrastructure} module.
 *
 * <p><strong>{@link #claim} is the only way a run comes into being</strong>, and the absence of an
 * insert beside it is the point rather than an omission. The guard is a lock taken inside the
 * claim, not a constraint on the table, so a second way to write a run row would step past it
 * silently — no error, just two imports hammering one source at once.
 *
 * <p>There is no delete, and an abandoned state cannot be written: a run past the abandonment
 * bound is read as abandoned and its row left exactly as the dead process left it.
 *
 * <p>Every method here records the run in a transaction of its own, whatever the caller is inside.
 * An import's own transaction and the record of it are deliberately not the same act — progress
 * has to be visible before a batch commits, and a bookkeeping failure must not roll imported
 * contracts back.
 */
public interface ImportRunRepository {

  /**
   * Records a run covering {@code coveredOrganos} and returns its identity, or refuses with an
   * empty answer because a live run already holds the guard. Refusing writes nothing.
   *
   * <p>The check and the write are one act — two triggers can both find no live run otherwise —
   * and the covered Órganos are enumerated here rather than discovered as the run reaches them,
   * so a run that dies partway can still name the list it was going to cover. A claim covering
   * none is a run that covers none, which is the catalogue import's shape.
   *
   * <p><strong>The coverage is (Órgano, family) pairs</strong>, so a trigger asking for both
   * families of one Órgano is still one claim. Claiming the second family separately would mean
   * asking for a guard the first claim is already holding.
   */
  Optional<ImportRunId> claim(Importer importer, Collection<CoveredOrgano> coveredOrganos);

  /**
   * Records a batch's progress against one covered Órgano's family, moving that row to in
   * progress, and proves the run is still alive. A run that stops advancing stops holding the
   * guard, so this is what a long import owes the rest of the system after every batch it commits.
   *
   * <p>The family is part of the address rather than a detail: a run can hold two rows for one
   * Órgano, and naming only the Órgano would move whichever of them the database returned first.
   */
  void advance(
      ImportRunId runId, OrganoId organoId, ContractFamily family, int added, int refreshed);

  /**
   * Settles one covered Órgano's family, with the reason a failed one failed and nothing
   * otherwise. The family addresses the row for the same reason {@link #advance} takes it.
   */
  void finishOrgano(
      ImportRunId runId,
      OrganoId organoId,
      ContractFamily family,
      ImportRunOrganoState state,
      @Nullable String failureReason);

  /**
   * Settles the run: its verdict, the moment it finished, and whatever it counted that no
   * {@link #advance} had already reported. The counts are <em>added</em> to the run's totals, on
   * the same rule an advance follows, so an importer that reports as it goes settles with zeroes
   * while one whose whole run is a single act carries all of it here — the parameters are named
   * for that, because passing the run's totals instead would count everything twice.
   *
   * <p>An importer covering no Órganos has no other way to record a count: an advance moves a
   * coverage row first and leaves the run's totals alone when there is none to move.
   *
   * @throws IllegalArgumentException if {@code verdict} is not one a run can be completed with
   */
  void complete(
      ImportRunId runId,
      ImportRunState verdict,
      int addedSinceLastAdvance,
      int refreshedSinceLastAdvance);

  /**
   * Whether this run is still the live one — it exists, and it has not gone quiet past the
   * abandonment bound. A long import owes itself this before each batch: a run that stalls past
   * the bound releases the guard and the next trigger claims, so one that wakes and carries on
   * would be reading the source alongside whoever claimed after it.
   *
   * <p>Distinct from {@link #findRun} rather than derived from it, because this runs once per
   * batch for days: it reads the run's own row and none of its coverage, where a report reads
   * every Órgano the run set out to cover in order to answer a question about one.
   *
   * <p>Ask it <em>before</em> advancing rather than after, because an advance is itself proof of
   * life — a run that renews its own last-advanced stamp and then asks whether it is live has
   * answered its own question.
   */
  boolean holdsGuard(ImportRunId runId);

  /** The run and every Órgano it covers, with the run's state already read for abandonment. */
  Optional<ImportRunReport> findRun(ImportRunId runId);
}
