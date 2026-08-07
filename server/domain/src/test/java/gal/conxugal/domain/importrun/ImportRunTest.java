package gal.conxugal.domain.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportRunTest {

  private static final Instant STARTED_AT = Instant.parse("2026-08-07T09:00:00Z");

  @Test
  void claimed_run_is_in_progress_counting_nothing_and_already_advancing() {
    ImportRun run = ImportRun.claimedAt(Importer.CONTRATOS_MENORES, STARTED_AT);

    assertThat(run)
        .extracting(
            ImportRun::id,
            ImportRun::importer,
            ImportRun::state,
            ImportRun::startedAt,
            ImportRun::finishedAt,
            ImportRun::lastAdvancedAt,
            ImportRun::added,
            ImportRun::refreshed)
        .containsExactly(
            null,
            Importer.CONTRATOS_MENORES,
            ImportRunState.IN_PROGRESS,
            STARTED_AT,
            null,
            STARTED_AT,
            0,
            0);
  }

  @Test
  void rejects_run_with_no_importer() {
    assertThatNullPointerException()
        .isThrownBy(() -> ImportRun.claimedAt(null, STARTED_AT));
  }

  @Test
  void is_the_same_run_however_far_its_counts_and_its_last_advance_have_moved() {
    ImportRunId id = new ImportRunId(UUID.randomUUID());
    ImportRun claimed = withIdentity(id, STARTED_AT, 0, 0);
    ImportRun advanced = withIdentity(id, STARTED_AT.plus(Duration.ofMinutes(5)), 100, 40);

    assertThat(claimed)
        .isEqualTo(advanced)
        .hasSameHashCodeAs(advanced);
  }

  @Test
  void differs_from_run_carrying_another_identity() {
    ImportRun run = withIdentity(new ImportRunId(UUID.randomUUID()), STARTED_AT, 0, 0);
    ImportRun other = withIdentity(new ImportRunId(UUID.randomUUID()), STARTED_AT, 0, 0);

    assertThat(run).isNotEqualTo(other);
  }

  @Test
  void run_the_database_has_not_identified_is_equal_only_to_itself() {
    ImportRun run = ImportRun.claimedAt(Importer.ORGANOS, STARTED_AT);
    ImportRun indistinguishable = ImportRun.claimedAt(Importer.ORGANOS, STARTED_AT);

    assertThat(run).isNotEqualTo(indistinguishable);
    assertThat(List.of(run, indistinguishable)).containsOnlyOnce(run);
  }

  private static ImportRun withIdentity(
      ImportRunId id, Instant lastAdvancedAt, int added, int refreshed) {
    return new ImportRun(
        id,
        Importer.CONTRATOS_MENORES,
        ImportRunState.IN_PROGRESS,
        STARTED_AT,
        null,
        lastAdvancedAt,
        added,
        refreshed);
  }
}
