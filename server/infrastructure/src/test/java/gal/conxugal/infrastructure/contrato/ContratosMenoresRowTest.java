package gal.conxugal.infrastructure.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ContratosMenoresRowTest {

  private static final int MAX_DURATION_LENGTH = ContratosMenoresRow.MAX_DURATION_LENGTH;
  private static final long SOURCE_ID = 2001090L;

  @Test
  void strips_the_padding_from_the_fixed_width_fields() {
    ContratosMenoresRow row = rowAwardedTo("33545498K           ", "  Talleres  López, S.L.   ");

    ContratoMenorSourceEntry entry = row.toSourceEntry();

    assertThat(entry)
        .extracting(
            ContratoMenorSourceEntry::awardeeFiscalId, ContratoMenorSourceEntry::awardeeName)
        .containsExactly("33545498K", "Talleres  López, S.L.");
  }

  @Test
  void carries_text_that_is_empty_once_stripped_as_absent() {
    ContratosMenoresRow row = rowAwardedTo("", "   ");

    ContratoMenorSourceEntry entry = row.toSourceEntry();

    assertThat(entry)
        .extracting(
            ContratoMenorSourceEntry::awardeeFiscalId, ContratoMenorSourceEntry::awardeeName)
        .containsOnlyNulls();
  }

  /**
   * The source contract once recorded a 60-character cap on the object that it does not have.
   * Nothing here may reintroduce one.
   */
  @Test
  void carries_the_object_at_its_full_published_length() {
    String objeto = "SERVIZOS TÉCNICOS DE ELECTRICIDADE ".repeat(20).strip();

    ContratoMenorSourceEntry entry = rowDescribing(objeto).toSourceEntry();

    assertThat(entry.obxecto()).isEqualTo(objeto);
  }

  @Test
  void caps_the_duration_at_the_maximum_when_it_is_longer() {
    ContratosMenoresRow row = rowLasting("z".repeat(MAX_DURATION_LENGTH + 1));

    ContratoMenorSourceEntry entry = row.toSourceEntry();

    assertThat(entry.duration()).isEqualTo("z".repeat(MAX_DURATION_LENGTH));
  }

  @Test
  void leaves_the_duration_untouched_at_exactly_the_maximum() {
    String duracion = "z".repeat(MAX_DURATION_LENGTH);

    ContratoMenorSourceEntry entry = rowLasting(duracion).toSourceEntry();

    assertThat(entry.duration()).isEqualTo(duracion);
  }

  /** The cap must not put back the trailing whitespace the strip took off. */
  @Test
  void leaves_no_trailing_whitespace_when_the_cap_lands_on_space() {
    String duracion = "z".repeat(MAX_DURATION_LENGTH - 1) + " meses e algo máis";

    ContratoMenorSourceEntry entry = rowLasting(duracion).toSourceEntry();

    assertThat(entry.duration()).isEqualTo("z".repeat(MAX_DURATION_LENGTH - 1));
  }

  /**
   * A cut through a surrogate pair yields a lone surrogate, which PostgreSQL refuses as an invalid
   * byte sequence — the failed batch the cap exists to avoid, arrived at by the cap itself.
   */
  @Test
  void backs_the_cap_off_rather_than_split_surrogate_pair() {
    String duracion = "z".repeat(MAX_DURATION_LENGTH - 1) + "😀 e algo máis";

    ContratoMenorSourceEntry entry = rowLasting(duracion).toSourceEntry();

    assertThat(entry.duration()).isEqualTo("z".repeat(MAX_DURATION_LENGTH - 1));
  }

  /** {@link Money} equality is scale-sensitive, so this fails if anything rescales the figure. */
  @Test
  void carries_the_amount_at_the_scale_the_source_published() {
    ContratosMenoresRow row = rowCosting(new BigDecimal("3630.00"));

    ContratoMenorSourceEntry entry = row.toSourceEntry();

    assertThat(entry.amount()).isEqualTo(new Money(new BigDecimal("3630.00")));
  }

  @Test
  void carries_an_absent_amount_as_absent() {
    ContratoMenorSourceEntry entry = rowCosting(null).toSourceEntry();

    assertThat(entry.amount()).isNull();
  }

  @Test
  void interprets_the_publication_date_from_its_published_text() {
    ContratoMenorSourceEntry entry = rowPublishedOn("05-05-2026").toSourceEntry();

    assertThat(entry.publicationDate()).isEqualTo(LocalDate.of(2026, 5, 5));
  }

  /** A date nobody can read is not a reason to refuse an award the source really published. */
  @Test
  void carries_an_uninterpretable_publication_date_as_absent() {
    ContratoMenorSourceEntry entry = rowPublishedOn("dunha vez").toSourceEntry();

    assertThat(entry.publicationDate()).isNull();
  }

  /**
   * The case the pattern alone cannot catch: a lenient resolver reads this as the 28th, storing a
   * date nobody published. Absent is the only honest answer.
   */
  @Test
  void carries_an_impossible_calendar_date_as_absent() {
    ContratoMenorSourceEntry entry = rowPublishedOn("31-02-2026").toSourceEntry();

    assertThat(entry.publicationDate()).isNull();
  }

  @Test
  void carries_the_source_identifier_the_row_was_published_under() {
    ContratoMenorSourceEntry entry = rowLasting("1 mes").toSourceEntry();

    assertThat(entry.sourceId()).isEqualTo(SOURCE_ID);
  }

  /** The one field nothing downstream can do without, so its absence is an unusable response. */
  @Test
  void throws_when_the_row_carries_no_identifier() {
    ContratosMenoresRow row =
        row(null, "05-05-2026", "Obxecto", BigDecimal.ONE, "33545498K", "Nome", "1 mes");

    assertThatThrownBy(row::toSourceEntry)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasNoCause();
  }

  private static ContratosMenoresRow rowAwardedTo(String nif, String adjudicatario) {
    return row(SOURCE_ID, "05-05-2026", "Obxecto", BigDecimal.ONE, nif, adjudicatario, "1 mes");
  }

  private static ContratosMenoresRow rowDescribing(String objeto) {
    return row(SOURCE_ID, "05-05-2026", objeto, BigDecimal.ONE, "33545498K", "Nome", "1 mes");
  }

  private static ContratosMenoresRow rowLasting(String duracion) {
    return row(SOURCE_ID, "05-05-2026", "Obxecto", BigDecimal.ONE, "33545498K", "Nome", duracion);
  }

  private static ContratosMenoresRow rowCosting(BigDecimal importe) {
    return row(SOURCE_ID, "05-05-2026", "Obxecto", importe, "33545498K", "Nome", "1 mes");
  }

  private static ContratosMenoresRow rowPublishedOn(String publicado) {
    return row(SOURCE_ID, publicado, "Obxecto", BigDecimal.ONE, "33545498K", "Nome", "1 mes");
  }

  private static ContratosMenoresRow row(
      Long id,
      String publicado,
      String objeto,
      BigDecimal importe,
      String nif,
      String adjudicatario,
      String duracion) {
    return new ContratosMenoresRow(id, publicado, objeto, importe, nif, adjudicatario, duracion);
  }
}
