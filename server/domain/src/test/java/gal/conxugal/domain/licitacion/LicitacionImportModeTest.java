package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LicitacionImportModeTest {

  // An Órgano with no state row reads as never started, which is what puts every Órgano already
  // marked for the other contract family into the initial mode with no migration and no re-marking.
  @Test
  void an_organo_that_was_never_imported_takes_the_initial_mode() {
    assertThat(LicitacionImportMode.of(LicitacionImportStatus.NEVER_STARTED))
        .isEqualTo(LicitacionImportMode.INITIAL);
  }

  // The distinction the three-state fact exists for: a half-loaded Órgano continues from its
  // cursor rather than re-walking a listing it has already read, and is never treated as loaded.
  @Test
  void an_organo_left_half_loaded_takes_the_resumed_mode() {
    assertThat(LicitacionImportMode.of(LicitacionImportStatus.INCOMPLETE))
        .isEqualTo(LicitacionImportMode.RESUMED);
  }

  @Test
  void an_organo_whose_history_is_loaded_takes_the_incremental_mode() {
    assertThat(LicitacionImportMode.of(LicitacionImportStatus.COMPLETE))
        .isEqualTo(LicitacionImportMode.INCREMENTAL);
  }

  // Every status maps somewhere, and no two share a mode — the property that makes a fourth status
  // impossible to add without deciding here what it imports.
  @Test
  void every_status_takes_its_own_mode() {
    assertThat(LicitacionImportStatus.values())
        .extracting(LicitacionImportMode::of)
        .doesNotContainNull()
        .doesNotHaveDuplicates()
        .hasSize(LicitacionImportStatus.values().length);
  }
}
