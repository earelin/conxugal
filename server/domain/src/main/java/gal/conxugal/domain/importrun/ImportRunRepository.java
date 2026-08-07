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
 * <p>There is no delete and no way to write an abandoned state: a run past the abandonment bound
 * is read as abandoned and its row left exactly as the dead process left it.
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
   * <p><strong>Call it outside any longer transaction.</strong> It takes a database lock that
   * releases on commit, so a caller holding it open holds the whole system's guard for that long.
   * It also relies on read-committed isolation: the loser's transaction begins before the winner
   * commits and sees the winner's row only because each statement takes a fresh snapshot.
   */
  Optional<ImportRunId> claim(Importer importer, Collection<OrganoId> coveredOrganos);

  /**
   * Records a batch's progress against one covered Órgano, moving it to in progress, and proves
   * the run is still alive. A run that stops advancing stops holding the guard, so this is what a
   * long import owes the rest of the system after every batch it commits.
   */
  void advance(ImportRunId runId, OrganoId organoId, int added, int refreshed);

  /** Settles one covered Órgano, with the reason a failed one failed and nothing otherwise. */
  void finishOrgano(
      ImportRunId runId,
      OrganoId organoId,
      ImportRunOrganoState state,
      @Nullable String failureReason);

  /** Settles the run: its verdict and the moment it finished. */
  void complete(ImportRunId runId, ImportRunState verdict);

  /** The run and every Órgano it covers, with the run's state already read for abandonment. */
  Optional<ImportRunReport> findRun(ImportRunId runId);
}
