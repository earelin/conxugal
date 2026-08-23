package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.organo.OrganoId;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class LicitacionImportStateTest {

  private static final OrganoId SERGAS =
      new OrganoId(UUID.fromString("0198f2c1-4e5a-7c3b-9d21-6f8a4b0c1d2e"));
  private static final OrganoId AUGAS =
      new OrganoId(UUID.fromString("0198f2c1-4e5a-7c3b-9d21-6f8a4b0c1d2f"));

  @Test
  void starting_an_import_leaves_it_incomplete_with_no_page_consumed() {
    LicitacionImportState state = LicitacionImportState.started(SERGAS);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(state.organoId()).isEqualTo(SERGAS);
      softly.assertThat(state.state()).isEqualTo(LicitacionImportStatus.INCOMPLETE);
      softly.assertThat(state.cursorOffset()).isNull();
    });
  }

  @Test
  void started_import_takes_the_resumed_mode() {
    assertThat(LicitacionImportState.started(SERGAS).mode())
        .isEqualTo(LicitacionImportMode.RESUMED);
  }

  @Test
  void completed_import_takes_the_incremental_mode() {
    LicitacionImportState completed =
        new LicitacionImportState(SERGAS, LicitacionImportStatus.COMPLETE, 16_800);

    assertThat(completed.mode()).isEqualTo(LicitacionImportMode.INCREMENTAL);
  }

  // The Órgano column keys the row, not the value: two readings of the same unchanged progress are
  // the same progress, and one taken after an advance is not.
  @Test
  void two_readings_of_the_same_unchanged_state_are_equal() {
    assertThat(new LicitacionImportState(SERGAS, LicitacionImportStatus.INCOMPLETE, 400))
        .isEqualTo(new LicitacionImportState(SERGAS, LicitacionImportStatus.INCOMPLETE, 400));
  }

  @Test
  void advancing_the_cursor_yields_another_value() {
    assertThat(new LicitacionImportState(SERGAS, LicitacionImportStatus.INCOMPLETE, 400))
        .isNotEqualTo(new LicitacionImportState(SERGAS, LicitacionImportStatus.INCOMPLETE, 500));
  }

  @Test
  void states_of_different_organos_are_not_equal() {
    assertThat(LicitacionImportState.started(SERGAS))
        .isNotEqualTo(LicitacionImportState.started(AUGAS));
  }

  @Test
  void refuses_progress_that_names_no_organo() {
    assertThatThrownBy(
            () -> new LicitacionImportState(null, LicitacionImportStatus.INCOMPLETE, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("organoId");
  }

  @Test
  void refuses_progress_that_states_no_status() {
    assertThatThrownBy(() -> new LicitacionImportState(SERGAS, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("state");
  }
}
