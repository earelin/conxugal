package gal.conxugal.domain.importrun;

/**
 * How one covered Órgano fared within a run.
 *
 * <p>{@link #PENDING} is what every covered Órgano starts at: the list is enumerated when the run
 * is triggered rather than discovered as the run reaches it, so a run that dies at the fortieth of
 * four hundred can still name what it was going to cover.
 *
 * <p>{@link #STOPPED} and {@link #SKIPPED} exist so the normal case is not reported as a failure.
 * An Órgano unmarked mid-run is stopped deliberately, keeping everything already stored; an
 * Órgano already complete is skipped because there is no incremental mode to run for it yet.
 * Neither is a fault of the run.
 */
public enum ImportRunOrganoState {
  PENDING,
  IN_PROGRESS,
  SUCCEEDED,
  FAILED,
  STOPPED,
  SKIPPED
}
