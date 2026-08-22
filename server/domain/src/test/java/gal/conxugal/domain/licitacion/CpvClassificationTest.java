package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CpvClassificationTest {

  private static final LicitacionId LICITACION_ID =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000f1"));
  private static final LoteId LOTE_ID =
      new LoteId(UUID.fromString("0198c0de-0000-7000-8000-0000000000a1"));
  private static final LocalDate DIFFUSED = LocalDate.of(2012, 6, 28);

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(
            Arrays.stream(CpvClassification.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("id", "licitacionId", "loteId", "code", "diffusionDate", "withdrawn");
  }

  @Test
  void constructs_against_the_procedure_as_whole_when_the_source_published_no_lote() {
    assertThat(procedureWide().loteId())
        .isNull();
  }

  @Test
  void constructs_against_lote_when_the_source_published_one() {
    assertThat(perLote().loteId())
        .isEqualTo(LOTE_ID);
  }

  @Test
  void accepts_procedure_wide_row_beside_the_per_lote_rows_of_one_procedure() {
    // Procedure 822054 has two lotes and two separate awards, and every CPV row's lote cell reads
    // the procedure-wide marker. A model requiring a lote here could not store what is published.
    List<CpvClassification> onOneProcedure = List.of(procedureWide(), perLote());

    assertThat(onOneProcedure)
        .extracting(CpvClassification::licitacionId)
        .containsOnly(LICITACION_ID);
    assertThat(onOneProcedure)
        .extracting(CpvClassification::loteId)
        .containsExactly(null, LOTE_ID);
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(procedureWide().id())
        .isNull();
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(procedureWide().withdrawn())
        .isFalse();
  }

  @Test
  void constructs_with_no_diffusion_date_because_one_that_could_not_be_read_is_absent() {
    assertThat(new CpvClassification(LICITACION_ID, null, "45000000", null).diffusionDate())
        .isNull();
  }

  @Test
  void strips_an_untrimmed_code_rather_than_keying_row_beside_the_trimmed_one() {
    assertThat(new CpvClassification(LICITACION_ID, null, " 45000000 ", DIFFUSED).code())
        .isEqualTo("45000000");
  }

  @Test
  void requires_the_procedure_it_belongs_to() {
    assertThatNullPointerException()
        .isThrownBy(() -> new CpvClassification(null, null, "45000000", DIFFUSED));
  }

  @Test
  void refuses_row_keyed_on_blank_code() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CpvClassification(LICITACION_ID, null, " \t", DIFFUSED));
  }

  @Test
  void is_the_same_classification_as_itself_whether_stored_or_not() {
    CpvClassification identified = stored(new CpvClassificationId(UUID.randomUUID()));
    CpvClassification sameIdentified = identified;
    CpvClassification unstored = procedureWide();
    CpvClassification sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_the_nut_classification_holding_the_same_values() {
    CpvClassification identified = stored(new CpvClassificationId(UUID.randomUUID()));

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new NutClassification(LICITACION_ID, null, "45000000", DIFFUSED));
  }

  @Test
  void treats_two_readings_of_one_stored_classification_as_the_same_classification() {
    CpvClassificationId id = new CpvClassificationId(UUID.randomUUID());

    assertThat(stored(id))
        .isEqualTo(stored(id))
        .hasSameHashCodeAs(stored(id));
  }

  @Test
  void stays_the_same_classification_when_it_is_withdrawn_underneath() {
    CpvClassificationId id = new CpvClassificationId(UUID.randomUUID());

    CpvClassification visible = stored(id);
    CpvClassification withdrawn =
        new CpvClassification(id, LICITACION_ID, null, "45000000", DIFFUSED, true);

    assertThat(visible)
        .isEqualTo(withdrawn)
        .hasSameHashCodeAs(withdrawn);
  }

  @Test
  void treats_classifications_the_database_has_not_identified_as_distinct() {
    assertThat(new HashSet<>(List.of(procedureWide(), procedureWide())))
        .hasSize(2);
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    CpvClassification unstored = procedureWide();
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  private static CpvClassification procedureWide() {
    return new CpvClassification(LICITACION_ID, null, "45000000", DIFFUSED);
  }

  private static CpvClassification perLote() {
    return new CpvClassification(LICITACION_ID, LOTE_ID, "45000000", DIFFUSED);
  }

  private static CpvClassification stored(CpvClassificationId id) {
    return new CpvClassification(id, LICITACION_ID, null, "45000000", DIFFUSED, false);
  }
}
