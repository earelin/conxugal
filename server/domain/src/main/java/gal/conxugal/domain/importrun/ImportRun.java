package gal.conxugal.domain.importrun;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One triggered import, written when it is claimed and completed in stages as it advances.
 * {@code id} is {@code null} only until the database assigns it.
 *
 * <p>{@code state} is what is <em>stored</em>, and it is never {@link ImportRunState#ABANDONED} —
 * that is derived on read from {@code lastAdvancedAt} and written nowhere. Which is also why
 * {@code lastAdvancedAt} is a column of its own rather than the latest of the counts' timestamps:
 * it is the guard's only evidence that the process behind an in-progress row is still alive, and a
 * long Órgano that adds nothing still has to prove it.
 *
 * <p>The counts are named for what they record rather than for what is counted, because the
 * catalogue import writes Órganos into the same two columns that the contratos menores import
 * writes contracts into.
 *
 * <p>The covered Órganos are not held here: they are rows of their own, enumerated at the claim,
 * and they are reached through {@link ImportRunReport} rather than through this entity, which
 * maps one table and nothing else.
 */
@MappedEntity("import_run")
public record ImportRun(
    @Id @GeneratedValue @Nullable ImportRunId id,
    Importer importer,
    ImportRunState state,
    Instant startedAt,
    @Nullable Instant finishedAt,
    Instant lastAdvancedAt,
    int added,
    int refreshed) {

  public ImportRun {
    Objects.requireNonNull(importer, "importer must not be null");
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    Objects.requireNonNull(lastAdvancedAt, "lastAdvancedAt must not be null");
  }

  /**
   * A run as its claim writes it: in progress, unfinished, counting nothing yet, and already
   * advancing as of the moment it was claimed — a run that has just started has not gone quiet.
   */
  public static ImportRun claimedAt(Importer importer, Instant startedAt) {
    return new ImportRun(null, importer, ImportRunState.IN_PROGRESS, startedAt, null, startedAt,
        0, 0);
  }

  /**
   * Identity, not contents: two instances are the same run when they carry the same assigned
   * {@link ImportRunId}. The record's own equality would compare the counts and the last-advanced
   * time, so the same row read either side of a batch would compare unequal to itself — and that
   * is the one thing a long run does constantly.
   *
   * <p>A run the database has not assigned an identity to is equal only to itself. There is
   * nothing else to match it on: two claims of the same importer at the same instant are two runs.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof ImportRun run && id != null && id.equals(run.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
