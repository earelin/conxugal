package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class ContratosMenoresImportStateTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");
  private static final Instant T_ONE = Instant.parse("2026-08-19T22:00:00Z");
  private static final Duration LOOKBACK = Duration.ofDays(30);

  @Test
  void starting_an_import_leaves_it_incomplete_stamped_and_without_cursor() {
    ContratosMenoresImportState state =
        ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(state.organoId()).isEqualTo(ORGANO_ID);
      softly.assertThat(state.state()).isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
      softly.assertThat(state.cursorDate()).isNull();
      softly.assertThat(state.coveredThrough()).isEqualTo(T_ZERO);
      softly.assertThat(state.refreshedThrough()).isNull();
    });
  }

  @Test
  void started_import_takes_the_resumed_mode() {
    ContratosMenoresImportState state =
        ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO);

    assertThat(state.mode()).isEqualTo(ContratosMenoresImportMode.RESUMED);
  }

  @Test
  void completed_import_takes_the_incremental_mode() {
    ContratosMenoresImportState state = new ContratosMenoresImportState(
        ORGANO_ID, ContratosMenoresImportStatus.COMPLETE, LocalDate.of(2018, 1, 1), T_ZERO, null);

    assertThat(state.mode()).isEqualTo(ContratosMenoresImportMode.INCREMENTAL);
  }

  // Contents, not identity: this is a value inside the Órgano aggregate, so advancing it yields a
  // different value. Comparing by the owner column instead would make a state equal to itself
  // across the very write that changes it, and no test of an advance could then fail.
  @Test
  void advancing_an_organos_state_yields_another_value() {
    ContratosMenoresImportState started =
        ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO);
    ContratosMenoresImportState advanced = new ContratosMenoresImportState(
        ORGANO_ID, ContratosMenoresImportStatus.COMPLETE, LocalDate.of(2019, 3, 31), T_ZERO, null);

    assertThat(started).isNotEqualTo(advanced);
  }

  @Test
  void two_readings_of_the_same_unchanged_state_are_equal() {
    ContratosMenoresImportState one = ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO);
    ContratosMenoresImportState other = ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(one).isEqualTo(other);
      softly.assertThat(one).hasSameHashCodeAs(other);
    });
  }

  @Test
  void states_of_different_organos_are_not_equal() {
    ContratosMenoresImportState state = ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO);
    ContratosMenoresImportState other =
        ContratosMenoresImportState.startedAt(new OrganoId(UUID.randomUUID()), T_ZERO);

    assertThat(state).isNotEqualTo(other);
  }

  @Test
  void refuses_state_without_the_instant_its_history_is_covered_through() {
    assertThatThrownBy(() -> new ContratosMenoresImportState(
        ORGANO_ID, ContratosMenoresImportStatus.INCOMPLETE, null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("coveredThrough");
  }

  // The fallback, and the reason the column is nullable: an Órgano's first refresh measures from
  // when its initial import *began*, so everything published while that import was walking — days,
  // for a large publisher — falls inside the window rather than into a hole nothing reads.
  @Test
  void never_refreshed_organo_takes_its_floor_from_the_instant_it_is_covered_through() {
    ContratosMenoresImportState state = ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO);

    assertThat(state.incrementalFloor(LOOKBACK))
        .isEqualTo(Instant.parse("2026-07-07T09:00:00Z"));
  }

  @Test
  void refreshed_organo_takes_its_floor_from_the_refresh_and_ignores_the_older_mark() {
    ContratosMenoresImportState state = refreshedThrough(T_ONE);

    assertThat(state.incrementalFloor(LOOKBACK))
        .isEqualTo(Instant.parse("2026-07-20T22:00:00Z"));
  }

  // A window is only as wide as the margin it is given, so a caller with none in hand has not
  // decided how far back corrections are looked for — answering T₁ itself would silently pick zero.
  @Test
  void refuses_to_answer_floor_without_lookback() {
    ContratosMenoresImportState state = refreshedThrough(T_ONE);

    assertThatThrownBy(() -> state.incrementalFloor(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("lookback");
  }

  private static ContratosMenoresImportState refreshedThrough(Instant refreshedThrough) {
    return new ContratosMenoresImportState(
        ORGANO_ID,
        ContratosMenoresImportStatus.COMPLETE,
        LocalDate.of(2018, 1, 1),
        T_ZERO,
        refreshedThrough);
  }
}
