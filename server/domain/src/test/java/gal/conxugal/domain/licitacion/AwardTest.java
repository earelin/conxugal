package gal.conxugal.domain.licitacion;

import static gal.conxugal.domain.licitacion.AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION;
import static gal.conxugal.domain.licitacion.AwardeeResolutionPath.UNRESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.OperadorId;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AwardTest {

  private static final LicitacionId LICITACION_ID =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000f1"));
  private static final LoteId LOTE_ID =
      new LoteId(UUID.fromString("0198c0de-0000-7000-8000-0000000000a1"));
  private static final OperadorId OPERADOR_ID =
      new OperadorId(UUID.fromString("0198c0de-0000-7000-8000-0000000000b1"));
  private static final Money AMOUNT = new Money(new BigDecimal("3052743.72"));
  private static final LocalDate RESOLVED_ON = LocalDate.of(2012, 6, 28);

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(Arrays.stream(Award.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly(
            "id",
            "licitacionId",
            "loteId",
            "resolution",
            "resolutionDate",
            "amount",
            "executionPeriod",
            "awardeeName",
            "operadorEconomicoId",
            "awardeeResolutionPath",
            "withdrawn");
  }

  @Test
  void holds_the_awarded_amount_rather_than_the_procedures_base_budget() {
    // For procedure 822054 the listing's importe is 3 378 552,09 — the base budget — while its two
    // lotes were awarded 3 052 743,72 and 206 996,66. Only the second kind belongs here.
    assertThat(perLote().amount())
        .isEqualTo(AMOUNT);
  }

  @Test
  void constructs_with_no_operador_because_an_award_naming_nobody_is_supported_outcome() {
    // 36% of measured award rows publish only a name, and nothing may turn that into a rejected
    // procedure: the licitación is stored and stays visible showing an award that names nobody.
    Award unresolved = unresolvedAward();

    assertThat(unresolved.operadorEconomicoId()).isNull();
    assertThat(unresolved.awardeeResolutionPath()).isEqualTo(UNRESOLVED);
  }

  @Test
  void keeps_the_published_awardee_name_even_where_no_operador_was_reached() {
    assertThat(unresolvedAward().awardeeName())
        .isEqualTo("ESQUEIRO, SL");
  }

  @Test
  void constructs_against_the_procedure_as_whole_when_it_has_no_lotes() {
    // 85 of 100 measured procedures have no lotes, so this is where the ordinary award hangs.
    assertThat(unresolvedAward().loteId())
        .isNull();
  }

  @Test
  void constructs_against_lote_independently_of_whether_it_names_an_operador() {
    Award resolvedPerLote = perLote();

    assertThat(resolvedPerLote.loteId()).isEqualTo(LOTE_ID);
    assertThat(resolvedPerLote.operadorEconomicoId()).isEqualTo(OPERADOR_ID);
    assertThat(resolvedPerLote.awardeeResolutionPath()).isEqualTo(PUBLISHED_BY_FORMALISATION);
  }

  @Test
  void holds_the_stated_execution_period_as_the_text_the_source_wrote() {
    assertThat(perLote().executionPeriod())
        .isEqualTo("2 meses 7 días");
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(unresolvedAward().id())
        .isNull();
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(unresolvedAward().withdrawn())
        .isFalse();
  }

  @Test
  void holds_every_published_value_that_could_not_be_read_as_null() {
    Award sparse =
        new Award(LICITACION_ID, null, null, null, null, null, null, null, UNRESOLVED);

    assertThat(sparse.resolution()).isNull();
    assertThat(sparse.resolutionDate()).isNull();
    assertThat(sparse.amount()).isNull();
    assertThat(sparse.executionPeriod()).isNull();
    assertThat(sparse.awardeeName()).isNull();
  }

  @Test
  void holds_the_text_that_published_only_whitespace_as_null() {
    Award blank =
        new Award(LICITACION_ID, null, " ", RESOLVED_ON, AMOUNT, "\t", "  ", null, UNRESOLVED);

    assertThat(blank.resolution()).isNull();
    assertThat(blank.executionPeriod()).isNull();
    assertThat(blank.awardeeName()).isNull();
  }

  @Test
  void requires_the_procedure_it_belongs_to() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new Award(null, null, "Adxudicado", RESOLVED_ON, AMOUNT, null, null, null,
                UNRESOLVED));
  }

  @Test
  void requires_the_resolution_path_so_an_unresolved_award_states_it_rather_than_leaving_null() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new Award(LICITACION_ID, null, "Adxudicado", RESOLVED_ON, AMOUNT, null, null,
                null, null));
  }

  @Test
  void is_the_same_award_as_itself_whether_stored_or_not() {
    Award identified = stored(new AwardId(UUID.randomUUID()), AMOUNT);
    Award sameIdentified = identified;
    Award unstored = unresolvedAward();
    Award sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_anything_that_is_not_award() {
    Award identified = stored(new AwardId(UUID.randomUUID()), AMOUNT);

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionContractType("Obras"));
  }

  @Test
  void treats_two_readings_of_one_stored_award_as_the_same_award() {
    AwardId id = new AwardId(UUID.randomUUID());

    assertThat(stored(id, AMOUNT))
        .isEqualTo(stored(id, AMOUNT))
        .hasSameHashCodeAs(stored(id, AMOUNT));
  }

  @Test
  void stays_the_same_award_when_the_source_restates_its_amount() {
    // A restatement corrects an award in place. Comparing by contents would call the corrected row
    // a different award, which is exactly what reconciliation must not conclude.
    AwardId id = new AwardId(UUID.randomUUID());

    Award before = stored(id, AMOUNT);
    Award after = stored(id, new Money(new BigDecimal("3052743.99")));

    assertThat(before)
        .isEqualTo(after)
        .hasSameHashCodeAs(after);
  }

  @Test
  void treats_awards_the_database_has_not_identified_as_distinct() {
    assertThat(new HashSet<>(List.of(unresolvedAward(), unresolvedAward())))
        .hasSize(2);
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    Award unstored = unresolvedAward();
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  private static Award unresolvedAward() {
    return new Award(
        LICITACION_ID,
        null,
        "Adxudicado",
        RESOLVED_ON,
        AMOUNT,
        "12 meses",
        "ESQUEIRO, SL",
        null,
        UNRESOLVED);
  }

  private static Award perLote() {
    return new Award(
        LICITACION_ID,
        LOTE_ID,
        "Adxudicado",
        RESOLVED_ON,
        AMOUNT,
        "2 meses 7 días",
        "XESTION AMBIENTAL DE CONTRATAS, S.L.",
        OPERADOR_ID,
        PUBLISHED_BY_FORMALISATION);
  }

  private static Award stored(AwardId id, Money amount) {
    return new Award(
        id,
        LICITACION_ID,
        LOTE_ID,
        "Adxudicado",
        RESOLVED_ON,
        amount,
        "12 meses",
        "ESQUEIRO, SL",
        OPERADOR_ID,
        PUBLISHED_BY_FORMALISATION,
        false);
  }
}
