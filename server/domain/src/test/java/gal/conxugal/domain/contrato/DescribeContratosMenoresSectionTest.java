package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The section's state is a matrix rather than a handful of cases, and it is walked as one: the two
 * flags are orthogonal, so every combination is a state the answer must be able to express and none
 * of them can be inferred from the others.
 *
 * <p>The Órgano is stubbed carrying its import state, because that is how the catalogue read
 * answers it — on one left join — and an Órgano with <b>no state row at all</b> is the case that
 * makes <em>never started</em> representable without a stored value standing for it.
 */
@ExtendWith(MockitoExtension.class)
class DescribeContratosMenoresSectionTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final Instant COVERED_THROUGH = Instant.parse("2025-03-01T00:00:00Z");
  private static final YearSelection THIS_YEAR = YearSelection.of(2025);
  private static final YearSelection LAST_YEAR = YearSelection.of(2024);

  @Mock
  private OrganoRepository organos;

  @Mock
  private VisibleContratoMenorRepository visibleContratos;

  // ------------------------------------------------------- which years, and whether there is one

  @Test
  void offers_exactly_the_years_the_facet_read_answered_in_the_order_it_answered_them() {
    organoIs(marked(ContratosMenoresImportStatus.COMPLETE));
    yearsAre(THIS_YEAR, LAST_YEAR);

    ContratosMenoresSection section = describe().orElseThrow();

    assertThat(section.years()).containsExactly(THIS_YEAR, LAST_YEAR);
  }

  @Test
  void answers_no_section_at_all_for_an_organo_with_no_visible_contracts() {
    organoIs(marked(ContratosMenoresImportStatus.COMPLETE));
    yearsAre();

    assertThat(describe()).isEmpty();
  }

  /**
   * The facet read withholds an anomalous contract, so an Órgano holding only those answers with
   * the same empty list one holding nothing answers with. What matters is not that each is absent
   * but that the two are <b>the same answer</b> — so both are taken here and compared, rather than
   * asserted absent one test apart where a later branch on the import status could tell them apart
   * and both assertions would still hold.
   */
  @Test
  void answers_for_an_organo_whose_contracts_are_all_anomalous_exactly_as_for_one_holding_none() {
    organoIs(marked(ContratosMenoresImportStatus.INCOMPLETE));
    yearsAre();
    Optional<ContratosMenoresSection> onlyAnomalous = describe();

    organoIs(marked(ContratosMenoresImportStatus.COMPLETE));
    Optional<ContratosMenoresSection> holdingNone = describe();

    assertThat(onlyAnomalous)
        .isEqualTo(holdingNone)
        .isEmpty();
  }

  @Test
  void answers_no_section_for_an_organo_that_does_not_exist() {
    when(organos.findById(ORGANO_ID)).thenReturn(Optional.empty());

    assertThat(describe()).isEmpty();
  }

  // --------------------------------------------------------------- every combination of the two

  @Test
  void complete_and_marked_is_neither_partial_nor_still_updating() {
    assertFlags(sectionOf(marked(ContratosMenoresImportStatus.COMPLETE)), false, true);
  }

  @Test
  void complete_and_unmarked_is_not_partial_and_is_no_longer_updated() {
    assertFlags(sectionOf(unmarked(ContratosMenoresImportStatus.COMPLETE)), false, false);
  }

  @Test
  void an_incomplete_and_marked_organo_is_partial_and_still_updating() {
    assertFlags(sectionOf(marked(ContratosMenoresImportStatus.INCOMPLETE)), true, true);
  }

  // The state one status could not express: unmarked halfway through an initial import, so both
  // that what is shown is incomplete and that nothing is going to complete it are true at once.
  @Test
  void an_incomplete_and_unmarked_organo_is_partial_and_no_longer_updated() {
    assertFlags(sectionOf(unmarked(ContratosMenoresImportStatus.INCOMPLETE)), true, false);
  }

  // Inactive is the catalogue's own withdrawal rather than the administrator's, and it stops the
  // refreshing exactly as unmarking does — while leaving how far the import got untouched.
  @Test
  void an_inactive_organo_is_no_longer_updated_though_it_is_still_marked() {
    assertFlags(
        sectionOf(organo(false, true, ContratosMenoresImportStatus.INCOMPLETE)), true, false);
  }

  @Test
  void an_inactive_and_fully_imported_organo_is_neither_partial_nor_updating() {
    assertFlags(
        sectionOf(organo(false, true, ContratosMenoresImportStatus.COMPLETE)), false, false);
  }

  // Never started has no stored value, so it is the absence of the state row — and it is partial
  // for the same reason an interrupted import is: what is shown is not all there is.
  @Test
  void an_organo_with_no_import_state_row_at_all_is_partial() {
    assertFlags(sectionOf(withNoImportState()), true, true);
  }

  /**
   * Each flag is asserted under its own name rather than by its position in a pair, so a failure
   * names the one that moved — and so a test's name can be read against what it asserts without
   * counting arguments back to a helper.
   */
  private static void assertFlags(
      ContratosMenoresSection section, boolean partial, boolean updating) {
    assertThat(section.partial())
        .as("partial")
        .isEqualTo(partial);
    assertThat(section.updating())
        .as("updating")
        .isEqualTo(updating);
  }

  private ContratosMenoresSection sectionOf(OrganoDeContratacion organo) {
    organoIs(organo);
    yearsAre(THIS_YEAR);

    return describe().orElseThrow();
  }

  private Optional<ContratosMenoresSection> describe() {
    return new DescribeContratosMenoresSection(organos, visibleContratos).describe(ORGANO_ID);
  }

  private void organoIs(OrganoDeContratacion organo) {
    when(organos.findById(ORGANO_ID)).thenReturn(Optional.of(organo));
  }

  private void yearsAre(YearSelection... years) {
    when(visibleContratos.years(ORGANO_ID)).thenReturn(List.of(years));
  }

  private static OrganoDeContratacion marked(ContratosMenoresImportStatus status) {
    return organo(true, true, status);
  }

  private static OrganoDeContratacion unmarked(ContratosMenoresImportStatus status) {
    return organo(true, false, status);
  }

  private static OrganoDeContratacion withNoImportState() {
    return organo(true, true, null);
  }

  private static OrganoDeContratacion organo(
      boolean active, boolean importable, @Nullable ContratosMenoresImportStatus status) {
    return new OrganoDeContratacion(
        ORGANO_ID,
        "source-key",
        "Consellería do Mar",
        active,
        importable,
        null,
        status == null
            ? null
            : new ContratosMenoresImportState(ORGANO_ID, status, null, COVERED_THROUGH));
  }
}
