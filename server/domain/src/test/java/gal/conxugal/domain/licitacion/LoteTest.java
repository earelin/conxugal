package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.money.Money;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoteTest {

  private static final LicitacionId LICITACION_ID =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000f1"));

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    // The record is the mapping, so a column with no component here is a column the store cannot
    // write. Pinned so a later change has to be deliberate.
    assertThat(Arrays.stream(Lote.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly(
            "id", "licitacionId", "identifier", "description", "estimatedValue", "withdrawn");
  }

  @Test
  void holds_the_identifier_as_text_so_an_integer_one_is_not_expressible() {
    // Pinning the name alone would let the component become an Integer and stay green, which is
    // the change that would reject OU0028 and every procedure carrying it.
    assertThat(
            Arrays.stream(Lote.class.getRecordComponents())
                .filter(component -> "identifier".equals(component.getName()))
                .map(component -> component.getType().getName()))
        .containsExactly(String.class.getName());
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(lote("1").id())
        .isNull();
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(lote("1").withdrawn())
        .isFalse();
  }

  @Test
  void constructs_under_an_alphanumeric_identifier_the_source_publishes() {
    // OU0028 was observed in an award-table lote cell. An integer component would have rejected
    // the procedure carrying it.
    assertThat(lote("OU0028").identifier())
        .isEqualTo("OU0028");
  }

  @Test
  void holds_the_padded_identifier_exactly_as_published() {
    // Storage keeps what the source printed; reducing 05 to 5 is LoteKey's job and is used for
    // nothing but comparison.
    assertThat(lote("05").identifier())
        .isEqualTo("05");
  }

  @Test
  void constructs_with_no_description_and_no_estimated_value() {
    // The lotes table was empty on procedure 822054 while the award table named both lotes, so a
    // lote's existence does not depend on its decoration.
    Lote bare = lote("2");

    assertThat(bare.description()).isNull();
    assertThat(bare.estimatedValue()).isNull();
  }

  @Test
  void carries_the_decoration_the_lotes_table_supplies_when_it_supplies_any() {
    Money estimated = new Money(new BigDecimal("206996.66"));

    Lote decorated = new Lote(LICITACION_ID, "2", "Subministración de equipamento", estimated);

    assertThat(decorated.description()).isEqualTo("Subministración de equipamento");
    assertThat(decorated.estimatedValue()).isEqualTo(estimated);
  }

  @Test
  void strips_an_untrimmed_identifier_rather_than_keying_lote_beside_the_trimmed_one() {
    assertThat(new Lote(LICITACION_ID, " 1 ", null, null).identifier())
        .isEqualTo("1");
  }

  @Test
  void holds_the_description_that_published_only_whitespace_as_null() {
    assertThat(new Lote(LICITACION_ID, "1", " \t", null).description())
        .isNull();
  }

  @Test
  void requires_the_procedure_it_belongs_to() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Lote(null, "1", null, null));
  }

  @Test
  void refuses_lote_keyed_on_blank_identifier() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Lote(LICITACION_ID, " \t", null, null));
  }

  @Test
  void is_the_same_lote_as_itself_whether_stored_or_not() {
    Lote identified = stored(new LoteId(UUID.randomUUID()), "1");
    Lote sameIdentified = identified;
    Lote unstored = lote("1");
    Lote sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_anything_that_is_not_lote() {
    Lote identified = stored(new LoteId(UUID.randomUUID()), "1");

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionContractType("Obras"));
  }

  @Test
  void treats_two_readings_of_one_stored_lote_as_the_same_lote() {
    LoteId id = new LoteId(UUID.randomUUID());

    assertThat(stored(id, "1"))
        .isEqualTo(stored(id, "1"))
        .hasSameHashCodeAs(stored(id, "1"));
  }

  @Test
  void stays_the_same_lote_when_its_description_arrives_underneath() {
    LoteId id = new LoteId(UUID.randomUUID());

    Lote before = stored(id, "1");
    Lote after = new Lote(id, LICITACION_ID, "1", "Obra civil", null, false);

    assertThat(before)
        .isEqualTo(after)
        .hasSameHashCodeAs(after);
  }

  @Test
  void treats_lotes_the_database_has_not_identified_as_distinct() {
    assertThat(new HashSet<>(List.of(lote("1"), lote("1"))))
        .hasSize(2);
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    Lote unstored = lote("1");
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  private static Lote lote(String identifier) {
    return new Lote(LICITACION_ID, identifier, null, null);
  }

  private static Lote stored(LoteId id, String identifier) {
    return new Lote(id, LICITACION_ID, identifier, null, null, false);
  }
}
