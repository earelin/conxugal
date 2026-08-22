package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.OrganoId;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicitacionTest {

  private static final long PUBLICATION_ID = 822054L;
  private static final OrganoId ORGANO_ID =
      new OrganoId(UUID.fromString("0198c0de-0000-7000-8000-00000000002a"));
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2024, 3, 8);
  private static final LocalDate MODIFIED_ON = LocalDate.of(2026, 8, 20);
  private static final LicitacionState FORMALIZADO = new LicitacionState(8, "Formalizado");
  private static final LicitacionContractType OBRAS = new LicitacionContractType("Obras");
  private static final LicitacionProcedureType ABERTOS = new LicitacionProcedureType("Abertos");
  private static final LicitacionTramitacionType ORDINARIA =
      new LicitacionTramitacionType("Ordinaria");
  private static final Money BASE_BUDGET = new Money(new BigDecimal("3378552.09"));
  private static final Money ESTIMATED_VALUE = new Money(new BigDecimal("2792191.81"));

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(published("Actuacións de mellora").id())
        .isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_publication_identifier() {
    LicitacionId id = new LicitacionId(UUID.randomUUID());

    Licitacion licitacion = stored(id, FORMALIZADO);

    assertThat(licitacion.id()).isEqualTo(id);
    assertThat(licitacion.publicationId()).isEqualTo(PUBLICATION_ID);
  }

  @Test
  void round_trips_every_published_field_it_was_built_with() {
    Licitacion licitacion = published("Actuacións de mellora");

    assertThat(licitacion.publicationId()).isEqualTo(PUBLICATION_ID);
    assertThat(licitacion.organoId()).isEqualTo(ORGANO_ID);
    assertThat(licitacion.publicationDate()).isEqualTo(PUBLISHED_ON);
    assertThat(licitacion.lastModified()).isEqualTo(MODIFIED_ON);
    assertThat(licitacion.expediente()).isEqualTo("2024/001");
    assertThat(licitacion.obxecto()).isEqualTo("Actuacións de mellora");
    assertThat(licitacion.loteCount()).isEqualTo(2);
    assertThat(licitacion.baseBudget()).isEqualTo(BASE_BUDGET);
    assertThat(licitacion.estimatedValue()).isEqualTo(ESTIMATED_VALUE);
  }

  @Test
  void reaches_both_halves_of_the_state_through_the_one_reference() {
    Licitacion licitacion = published("Actuacións de mellora");

    assertThat(licitacion.state().code()).isEqualTo(8);
    assertThat(licitacion.state().label()).isEqualTo("Formalizado");
  }

  @Test
  void reaches_each_published_type_through_its_own_reference() {
    Licitacion licitacion = published("Actuacións de mellora");

    assertThat(licitacion.contractType().name()).isEqualTo("Obras");
    assertThat(licitacion.procedureType().name()).isEqualTo("Abertos");
    assertThat(licitacion.tramitacionType().name()).isEqualTo("Ordinaria");
  }

  @Test
  void requires_the_convening_organo() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Licitacion(
                    PUBLICATION_ID,
                    null,
                    PUBLISHED_ON,
                    MODIFIED_ON,
                    FORMALIZADO,
                    "2024/001",
                    "Obras",
                    OBRAS,
                    ABERTOS,
                    ORDINARIA,
                    2,
                    BASE_BUDGET,
                    ESTIMATED_VALUE));
  }

  @Test
  void requires_the_published_state_because_the_listing_always_carries_one() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Licitacion(
                    PUBLICATION_ID,
                    ORGANO_ID,
                    PUBLISHED_ON,
                    MODIFIED_ON,
                    null,
                    "2024/001",
                    "Obras",
                    OBRAS,
                    ABERTOS,
                    ORDINARIA,
                    2,
                    BASE_BUDGET,
                    ESTIMATED_VALUE));
  }

  @Test
  void stores_the_procedure_whose_record_published_none_of_the_three_types() {
    Licitacion licitacion = sparse();

    assertThat(licitacion.contractType()).isNull();
    assertThat(licitacion.procedureType()).isNull();
    assertThat(licitacion.tramitacionType()).isNull();
  }

  @Test
  void stores_the_procedure_the_source_published_nothing_else_about() {
    Licitacion licitacion = sparse();

    assertThat(licitacion.publicationDate()).isNull();
    assertThat(licitacion.lastModified()).isNull();
    assertThat(licitacion.expediente()).isNull();
    assertThat(licitacion.obxecto()).isNull();
    assertThat(licitacion.loteCount()).isNull();
  }

  @Test
  void keeps_an_uninterpretable_publication_date_null_without_refusing_the_procedure() {
    Licitacion licitacion =
        new Licitacion(
            PUBLICATION_ID,
            ORGANO_ID,
            null,
            MODIFIED_ON,
            FORMALIZADO,
            "2024/001",
            "Obras",
            OBRAS,
            ABERTOS,
            ORDINARIA,
            2,
            BASE_BUDGET,
            ESTIMATED_VALUE);

    assertThat(licitacion.publicationDate()).isNull();
    assertThat(licitacion.obxecto()).isEqualTo("Obras");
  }

  @Test
  void constructs_with_neither_economic_figure_published() {
    Licitacion licitacion = sparse();

    assertThat(licitacion.baseBudget()).isNull();
    assertThat(licitacion.estimatedValue()).isNull();
  }

  @Test
  void keeps_both_economic_figures_exactly_as_published() {
    Licitacion licitacion = published("Actuacións de mellora");

    assertThat(licitacion.baseBudget())
        .isEqualTo(new Money(new BigDecimal("3378552.09")));
    assertThat(licitacion.estimatedValue())
        .isEqualTo(new Money(new BigDecimal("2792191.81")));
  }

  @Test
  void keeps_the_object_exactly_as_published_including_internal_spacing() {
    String obxecto = "ACTUACIÓNS   de mellora  enerxética";

    assertThat(published(obxecto).obxecto())
        .isEqualTo(obxecto);
  }

  @Test
  void keeps_the_object_at_whatever_length_the_source_published() {
    String obxecto = "Actuacións de mellora de infraestruturas ".repeat(200);

    assertThat(published(obxecto).obxecto())
        .isEqualTo(obxecto)
        .hasSize(obxecto.length());
  }

  @Test
  void keeps_the_object_exactly_as_the_adapter_handed_it_over() {
    // The trim is the adapter's and this aggregate does none of its own — which only `nullIfBlank`
    // in the compact constructor comes near, so a "tidy-up" that turned it into a strip would
    // otherwise pass every test here.
    assertThat(published("  Actuacións de mellora  ").obxecto())
        .isEqualTo("  Actuacións de mellora  ");
  }

  @Test
  void keeps_the_expediente_exactly_as_the_adapter_handed_it_over() {
    Licitacion licitacion =
        new Licitacion(
            PUBLICATION_ID,
            ORGANO_ID,
            PUBLISHED_ON,
            MODIFIED_ON,
            FORMALIZADO,
            "  2024/001  ",
            "Obras",
            OBRAS,
            ABERTOS,
            ORDINARIA,
            2,
            BASE_BUDGET,
            ESTIMATED_VALUE);

    assertThat(licitacion.expediente()).isEqualTo("  2024/001  ");
  }

  @Test
  void holds_an_object_that_published_only_whitespace_as_null() {
    assertThat(published("   ").obxecto())
        .isNull();
  }

  @Test
  void holds_an_expediente_that_published_only_whitespace_as_null() {
    Licitacion licitacion =
        new Licitacion(
            PUBLICATION_ID,
            ORGANO_ID,
            PUBLISHED_ON,
            MODIFIED_ON,
            FORMALIZADO,
            " \t",
            "Obras",
            OBRAS,
            ABERTOS,
            ORDINARIA,
            2,
            BASE_BUDGET,
            ESTIMATED_VALUE);

    assertThat(licitacion.expediente()).isNull();
  }

  @Test
  void is_not_withdrawn_when_it_is_first_read_from_the_source() {
    assertThat(published("Actuacións de mellora").withdrawn())
        .isFalse();
  }

  @Test
  void holds_no_second_copy_of_what_its_children_carry() {
    // R8 promises exactly one place for the award, the classification and the formalisation. A
    // component reintroducing any of them here would be the second copy, and two would be one too
    // many to keep in step.
    //
    // It pins the vocabularies too: no `stateCode`, no `stateLabel`, no type name. A procedure
    // holding the label instead of the reference could not tell 101 from 102, both of which are
    // published as Histórico.
    assertThat(Arrays.stream(Licitacion.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly(
            "id",
            "publicationId",
            "organoId",
            "publicationDate",
            "lastModified",
            "state",
            "expediente",
            "obxecto",
            "contractType",
            "procedureType",
            "tramitacionType",
            "loteCount",
            "baseBudget",
            "estimatedValue",
            "withdrawn");
  }

  @Test
  void treats_two_readings_of_one_stored_procedure_as_the_same_procedure() {
    LicitacionId id = new LicitacionId(UUID.randomUUID());

    Licitacion read = stored(id, FORMALIZADO);
    Licitacion reread = stored(id, FORMALIZADO);

    assertThat(read)
        .isEqualTo(reread)
        .hasSameHashCodeAs(reread);
  }

  @Test
  void stays_the_same_procedure_when_its_state_row_changes_underneath() {
    LicitacionId id = new LicitacionId(UUID.randomUUID());
    LicitacionStateId stateId = new LicitacionStateId(UUID.randomUUID());

    Licitacion beforeCorrection =
        stored(id, new LicitacionState(stateId, 8, "Formalizado"));
    Licitacion afterCorrection =
        stored(id, new LicitacionState(stateId, 8, "Formalizado no perfil"));

    // Contents-based equality would compare the four joined vocabulary rows, so the same stored
    // row would stop being equal to itself the moment one of their labels was corrected.
    assertThat(beforeCorrection)
        .isEqualTo(afterCorrection)
        .hasSameHashCodeAs(afterCorrection);
  }

  @Test
  void separates_procedures_carrying_different_identities() {
    Licitacion first = stored(new LicitacionId(UUID.randomUUID()), FORMALIZADO);
    Licitacion second = stored(new LicitacionId(UUID.randomUUID()), FORMALIZADO);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void treats_procedures_the_database_has_not_identified_as_distinct() {
    Licitacion first = published("Actuacións de mellora");
    Licitacion second = published("Actuacións de mellora");

    assertThat(first)
        .isNotEqualTo(second);
    // Two readings of the same publication, neither stored yet: each is only itself, so the same
    // instance twice collapses and the two distinct ones do not.
    assertThat(new HashSet<>(List.of(first, second, first)))
        .hasSize(2);
  }

  @Test
  void separates_an_identified_procedure_from_one_not_yet_stored() {
    Licitacion identified = stored(new LicitacionId(UUID.randomUUID()), FORMALIZADO);

    assertThat(identified).isNotEqualTo(published("Actuacións de mellora"));
  }

  private static Licitacion published(String obxecto) {
    return new Licitacion(
        PUBLICATION_ID,
        ORGANO_ID,
        PUBLISHED_ON,
        MODIFIED_ON,
        FORMALIZADO,
        "2024/001",
        obxecto,
        OBRAS,
        ABERTOS,
        ORDINARIA,
        2,
        BASE_BUDGET,
        ESTIMATED_VALUE);
  }

  /** An open procedure whose record published nothing but the listing's own four values. */
  private static Licitacion sparse() {
    return new Licitacion(
        PUBLICATION_ID,
        ORGANO_ID,
        null,
        null,
        new LicitacionState(1, "En curso"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static Licitacion stored(LicitacionId id, LicitacionState state) {
    return new Licitacion(
        id,
        PUBLICATION_ID,
        ORGANO_ID,
        PUBLISHED_ON,
        MODIFIED_ON,
        state,
        "2024/001",
        "Actuacións de mellora",
        OBRAS,
        ABERTOS,
        ORDINARIA,
        2,
        BASE_BUDGET,
        ESTIMATED_VALUE,
        false);
  }
}
