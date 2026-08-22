package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FormalisationTest {

  private static final LicitacionId LICITACION_ID =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000f1"));
  private static final LoteId LOTE_ID =
      new LoteId(UUID.fromString("0198c0de-0000-7000-8000-0000000000a1"));
  private static final FiscalIdentifier EQUINSE = new FiscalIdentifier("A41111220");
  private static final Money AMOUNT = new Money(new BigDecimal("25627.12"));
  private static final LocalDate SIGNED_ON = LocalDate.of(2012, 6, 28);

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(
            Arrays.stream(Formalisation.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "id",
            "licitacionId",
            "loteId",
            "formalisationDate",
            "contratistaName",
            "fiscalIdentifier",
            "nationality",
            "amount",
            "withdrawn");
  }

  @Test
  void carries_the_identifier_the_contratista_cell_published_beside_the_name() {
    // The Contratista cell holds both — "EQUINSE, S.A. A41111220" — and it is the route that
    // resolves 58% of awards.
    Formalisation identified = perLote();

    assertThat(identified.contratistaName()).isEqualTo("EQUINSE, S.A.");
    assertThat(identified.fiscalIdentifier()).isEqualTo(EQUINSE);
  }

  @Test
  void constructs_with_no_fiscal_identifier_when_the_trailing_token_was_not_shaped_like_one() {
    // The cell yields the name alone rather than a rejected row; resolution falls to another
    // route.
    assertThat(withoutIdentifier().fiscalIdentifier())
        .isNull();
  }

  @Test
  void constructs_against_the_procedure_as_whole_when_it_has_no_lotes() {
    assertThat(withoutIdentifier().loteId())
        .isNull();
  }

  @Test
  void constructs_against_lote_when_the_source_published_one() {
    assertThat(perLote().loteId())
        .isEqualTo(LOTE_ID);
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(withoutIdentifier().id())
        .isNull();
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(withoutIdentifier().withdrawn())
        .isFalse();
  }

  @Test
  void holds_every_published_value_that_could_not_be_read_as_null() {
    Formalisation sparse = new Formalisation(LICITACION_ID, null, null, null, null, null, null);

    assertThat(sparse.formalisationDate()).isNull();
    assertThat(sparse.contratistaName()).isNull();
    assertThat(sparse.nationality()).isNull();
    assertThat(sparse.amount()).isNull();
  }

  @Test
  void holds_the_text_that_published_only_whitespace_as_null() {
    Formalisation blank =
        new Formalisation(LICITACION_ID, null, SIGNED_ON, " ", null, "\t", AMOUNT);

    assertThat(blank.contratistaName()).isNull();
    assertThat(blank.nationality()).isNull();
  }

  @Test
  void requires_the_procedure_it_belongs_to() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new Formalisation(null, null, SIGNED_ON, "EQUINSE, S.A.", EQUINSE, "España",
                AMOUNT));
  }

  @Test
  void is_the_same_formalisation_as_itself_whether_stored_or_not() {
    Formalisation identified = stored(new FormalisationId(UUID.randomUUID()), AMOUNT);
    Formalisation sameIdentified = identified;
    Formalisation unstored = withoutIdentifier();
    Formalisation sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_anything_that_is_not_formalisation() {
    Formalisation identified = stored(new FormalisationId(UUID.randomUUID()), AMOUNT);

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionContractType("Obras"));
  }

  @Test
  void treats_two_readings_of_one_stored_formalisation_as_the_same_formalisation() {
    FormalisationId id = new FormalisationId(UUID.randomUUID());

    assertThat(stored(id, AMOUNT))
        .isEqualTo(stored(id, AMOUNT))
        .hasSameHashCodeAs(stored(id, AMOUNT));
  }

  @Test
  void stays_the_same_formalisation_when_the_source_restates_its_amount() {
    FormalisationId id = new FormalisationId(UUID.randomUUID());

    Formalisation before = stored(id, AMOUNT);
    Formalisation after = stored(id, new Money(new BigDecimal("25627.99")));

    assertThat(before)
        .isEqualTo(after)
        .hasSameHashCodeAs(after);
  }

  @Test
  void treats_formalisations_the_database_has_not_identified_as_distinct() {
    assertThat(new HashSet<>(List.of(withoutIdentifier(), withoutIdentifier())))
        .hasSize(2);
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    Formalisation unstored = withoutIdentifier();
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  private static Formalisation perLote() {
    return new Formalisation(
        LICITACION_ID, LOTE_ID, SIGNED_ON, "EQUINSE, S.A.", EQUINSE, "España", AMOUNT);
  }

  private static Formalisation withoutIdentifier() {
    return new Formalisation(
        LICITACION_ID, null, SIGNED_ON, "EQUINSE, S.A.", null, "España", AMOUNT);
  }

  private static Formalisation stored(FormalisationId id, Money amount) {
    return new Formalisation(
        id, LICITACION_ID, LOTE_ID, SIGNED_ON, "EQUINSE, S.A.", EQUINSE, "España", amount, false);
  }
}
