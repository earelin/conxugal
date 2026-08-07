package gal.conxugal.domain.importrun;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A run as a reader sees it: what it amounts to, when it ran, what it counted, and every Órgano it
 * set out to cover with that Órgano's own state and counts.
 *
 * <p>{@code state} is already derived — a run past the abandonment bound reads as
 * {@link ImportRunState#ABANDONED} here even though its row still says in progress. That is why
 * this record carries no last-advanced time: with one, a reader could re-derive the rule and
 * disagree with the guard about it; without one, the derived state is the only answer available.
 */
public record ImportRunReport(
    ImportRunId id,
    Importer importer,
    ImportRunState state,
    Instant startedAt,
    @Nullable Instant finishedAt,
    int added,
    int refreshed,
    List<ImportRunOrganoCoverage> coveredOrganos) {

  public ImportRunReport {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(importer, "importer must not be null");
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    Objects.requireNonNull(coveredOrganos, "coveredOrganos must not be null");
    coveredOrganos = List.copyOf(coveredOrganos);
  }
}
