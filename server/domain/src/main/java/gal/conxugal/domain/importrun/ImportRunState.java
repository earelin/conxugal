package gal.conxugal.domain.importrun;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * What a run amounts to. Four verdicts and one derivation.
 *
 * <p>{@link #PARTIALLY_SUCCEEDED} is not a nicety: a run carries on past an Órgano that failed, so
 * a binary succeeded-or-failed would report the normal case as a lie.
 *
 * <p><strong>{@link #ABANDONED} is never stored.</strong> Nothing sweeps run rows, so a process
 * that dies mid-run leaves its row saying {@link #IN_PROGRESS} for good; without a rule that reads
 * such a row as dead, one crash would hold the system's single-import guard forever. The rule is
 * {@link #asReadAt}, and it is the only place it exists — a second copy is a reader that can
 * disagree with the guard about whether an import may start.
 */
public enum ImportRunState {
  IN_PROGRESS,
  SUCCEEDED,
  PARTIALLY_SUCCEEDED,
  FAILED,
  ABANDONED;

  /**
   * This state as a reader sees it: {@link #ABANDONED} when the run is still in progress but has
   * not advanced within {@code abandonmentBound}, and unchanged otherwise. A run that has finished
   * cannot become abandoned however long ago it did so.
   *
   * <p>Strictly older releases the guard, so a run advanced exactly at the bound still holds it —
   * the bound is how long a live run may go quiet, not the first moment it is doubted.
   */
  public ImportRunState asReadAt(Instant lastAdvancedAt, Instant now, Duration abandonmentBound) {
    Objects.requireNonNull(lastAdvancedAt, "lastAdvancedAt must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(abandonmentBound, "abandonmentBound must not be null");
    if (this != IN_PROGRESS) {
      return this;
    }
    return lastAdvancedAt.isBefore(now.minus(abandonmentBound)) ? ABANDONED : IN_PROGRESS;
  }
}
