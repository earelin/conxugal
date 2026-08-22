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

class NutClassificationTest {

  private static final LicitacionId LICITACION_ID =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000f1"));
  private static final LoteId LOTE_ID =
      new LoteId(UUID.fromString("0198c0de-0000-7000-8000-0000000000a1"));
  private static final LocalDate DIFFUSED = LocalDate.of(2012, 6, 28);

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(
            Arrays.stream(NutClassification.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("id", "licitacionId", "loteId", "code", "diffusionDate", "withdrawn");
  }

  @Test
  void constructs_against_the_procedure_as_whole_when_the_source_published_no_lote() {
    // Over 240 procedures the NUT table wrote the procedure-wide marker on 217 rows, so this is
    // the ordinary case here rather than the exception.
    assertThat(procedureWide().loteId())
        .isNull();
  }

  @Test
  void constructs_against_lote_when_the_source_published_one() {
    assertThat(new NutClassification(LICITACION_ID, LOTE_ID, "ES111", DIFFUSED).loteId())
        .isEqualTo(LOTE_ID);
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
    assertThat(new NutClassification(LICITACION_ID, null, "ES111", null).diffusionDate())
        .isNull();
  }

  @Test
  void strips_an_untrimmed_code_rather_than_keying_row_beside_the_trimmed_one() {
    assertThat(new NutClassification(LICITACION_ID, null, " ES111 ", DIFFUSED).code())
        .isEqualTo("ES111");
  }

  @Test
  void requires_the_procedure_it_belongs_to() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NutClassification(null, null, "ES111", DIFFUSED));
  }

  @Test
  void refuses_row_keyed_on_blank_code() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NutClassification(LICITACION_ID, null, " \t", DIFFUSED));
  }

  @Test
  void is_the_same_classification_as_itself_whether_stored_or_not() {
    NutClassification identified = stored(new NutClassificationId(UUID.randomUUID()));
    NutClassification sameIdentified = identified;
    NutClassification unstored = procedureWide();
    NutClassification sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_the_cpv_classification_holding_the_same_values() {
    NutClassification identified = stored(new NutClassificationId(UUID.randomUUID()));

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new CpvClassification(LICITACION_ID, null, "ES111", DIFFUSED));
  }

  @Test
  void treats_two_readings_of_one_stored_classification_as_the_same_classification() {
    NutClassificationId id = new NutClassificationId(UUID.randomUUID());

    assertThat(stored(id))
        .isEqualTo(stored(id))
        .hasSameHashCodeAs(stored(id));
  }

  @Test
  void stays_the_same_classification_when_it_is_withdrawn_underneath() {
    NutClassificationId id = new NutClassificationId(UUID.randomUUID());

    NutClassification visible = stored(id);
    NutClassification withdrawn =
        new NutClassification(id, LICITACION_ID, null, "ES111", DIFFUSED, true);

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
    NutClassification unstored = procedureWide();
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  private static NutClassification procedureWide() {
    return new NutClassification(LICITACION_ID, null, "ES111", DIFFUSED);
  }

  private static NutClassification stored(NutClassificationId id) {
    return new NutClassification(id, LICITACION_ID, null, "ES111", DIFFUSED, false);
  }
}
