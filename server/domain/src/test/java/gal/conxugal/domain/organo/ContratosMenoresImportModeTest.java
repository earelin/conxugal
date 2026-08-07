package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContratosMenoresImportModeTest {

  @Test
  void an_organo_that_was_never_imported_takes_the_initial_mode() {
    assertThat(ContratosMenoresImportMode.of(ContratosMenoresImportStatus.NEVER_STARTED))
        .isEqualTo(ContratosMenoresImportMode.INITIAL);
  }

  // The distinction the three-state fact exists for: a half-loaded Órgano continues rather than
  // restarting a multi-day walk, and is never treated as up to date.
  @Test
  void an_organo_left_half_loaded_takes_the_resumed_mode() {
    assertThat(ContratosMenoresImportMode.of(ContratosMenoresImportStatus.INCOMPLETE))
        .isEqualTo(ContratosMenoresImportMode.RESUMED);
  }

  @Test
  void an_organo_whose_history_is_loaded_takes_the_incremental_mode() {
    assertThat(ContratosMenoresImportMode.of(ContratosMenoresImportStatus.COMPLETE))
        .isEqualTo(ContratosMenoresImportMode.INCREMENTAL);
  }

  // Every status maps somewhere, and no two share a mode — the property that makes the rule
  // decidable from the Órgano's own state rather than from whichever trigger arrived.
  @Test
  void every_status_takes_its_own_mode() {
    assertThat(ContratosMenoresImportStatus.values())
        .extracting(ContratosMenoresImportMode::of)
        .doesNotContainNull()
        .doesNotHaveDuplicates()
        .hasSize(ContratosMenoresImportStatus.values().length);
  }
}
