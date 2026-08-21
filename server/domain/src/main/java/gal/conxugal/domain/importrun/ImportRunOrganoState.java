package gal.conxugal.domain.importrun;

/**
 * How one covered Órgano fared within a run.
 *
 * <p>{@link #PENDING} is what every covered Órgano starts at: the list is enumerated when the run
 * is triggered rather than discovered as the run reaches it, so a run that dies at the fortieth of
 * four hundred can still name what it was going to cover.
 *
 * <p>{@link #STOPPED} exists so the normal case is not reported as a failure: an Órgano is stopped
 * when it was unmarked — before the run reached it, or at a batch boundary while it was being
 * walked — keeping everything already stored, which is no fault of the run.
 *
 * <p><strong>{@link #SKIPPED} is history and nothing settles it any more.</strong> It was what an
 * already-loaded Órgano was recorded as while there was no incremental mode to run for it; such an
 * Órgano is now refreshed. The value is kept because runs recorded before then still carry it, and
 * a value removed is a stored row that no longer reads.
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
