package gal.conxugal.domain.importrun;

/**
 * How one covered Órgano fared within a run.
 *
 * <p>{@link #PENDING} is what every covered Órgano starts at: the list is enumerated when the run
 * is triggered rather than discovered as the run reaches it, so a run that dies at the fortieth of
 * four hundred can still name what it was going to cover.
 *
 * <p>{@link #STOPPED} and {@link #SKIPPED} exist so the normal case is not reported as a failure.
 * An Órgano is stopped when it was unmarked — before the run reached it, or at a batch boundary
 * while it was being walked — keeping everything already stored; an Órgano already complete is
 * skipped because there is no incremental mode to run for it yet. Neither is a fault of the run.
 *
 * <p><strong>A run that stops holding the guard settles no row at all.</strong> Its coverage is
 * left exactly as the process that gave up left it, which is why a run read as abandoned can carry
 * a row still saying {@link #IN_PROGRESS}: nothing was ever going to write its ending, because by
 * then the record belonged to whoever claimed the guard next.
 */
public enum ImportRunOrganoState {
  PENDING,
  IN_PROGRESS,
  SUCCEEDED,
  FAILED,
  STOPPED,
  SKIPPED
}
