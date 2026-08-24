package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.PublishedAmount;
import gal.conxugal.domain.licitacion.VatBasis;
import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** What the record's own notation means, which is the half no ASCII fixture would have caught. */
class PublishedValuesTest {

  /**
   * The record's base budget, exactly as its markup lays it out. A default locale parse reads this
   * as {@code 3.378} — a plausible number, wrong by six orders of magnitude, and silent — which is
   * why the expected value is asserted against that answer explicitly.
   */
  @Test
  void reads_the_galician_grouped_amount_rather_than_the_locale_default() {
    PublishedAmount parsed = PublishedValues.amount("3.378.552,09 con IVE");

    assertThat(parsed)
        .isNotNull()
        .extracting(PublishedAmount::amount)
        .isEqualTo(new Money(new BigDecimal("3378552.09")))
        .isNotEqualTo(new Money(new BigDecimal("3.378")));
  }

  @Test
  void reads_the_vat_basis_the_source_labelled_the_figure_with() {
    assertThat(PublishedValues.amount("3.378.552,09 con IVE"))
        .extracting(PublishedAmount::vatBasis)
        .isEqualTo(VatBasis.INCLUSIVE);
    assertThat(PublishedValues.amount("2.792.191,81 sen IVE"))
        .extracting(PublishedAmount::vatBasis)
        .isEqualTo(VatBasis.EXCLUSIVE);
  }

  /**
   * The record breaks a figure and its marker across four lines. Layout carries no meaning in a
   * number, so it is flattened — unlike a text value, whose internal spacing is preserved.
   */
  @Test
  void reads_an_amount_the_markup_split_across_lines() {
    PublishedAmount parsed =
        PublishedValues.amount("\n     3.378.552,09\n     \n     \n       con IVE\n   ");

    assertThat(parsed)
        .isNotNull()
        .extracting(PublishedAmount::amount)
        .isEqualTo(new Money(new BigDecimal("3378552.09")));
  }

  /**
   * The award table's form. Its currency mark reaches the parse as {@code €} because the source
   * writes it as a numeric entity, which jsoup decodes whatever the document's charset is — and no
   * row of it carries a tax marker, so the basis is genuinely nothing rather than assumed.
   */
  @Test
  void reads_the_currency_marked_amount_and_leaves_its_basis_unstated() {
    assertThat(PublishedValues.amount("206.996,66 €"))
        .isEqualTo(new PublishedAmount(new Money(new BigDecimal("206996.66")), null));
  }

  /** No published figure carries a sign, so one that does is text rather than an amount. */
  @Test
  void answers_no_amount_for_the_signed_figure() {
    assertThat(PublishedValues.amount("-1.000,00 con IVE")).isNull();
  }

  /** {@link Money} compares scale-sensitively, so a published figure must not be renormalised. */
  @Test
  void keeps_the_scale_the_source_published() {
    assertThat(PublishedValues.amount("1.000,50 con IVE"))
        .extracting(PublishedAmount::amount)
        .isEqualTo(new Money(new BigDecimal("1000.50")))
        .isNotEqualTo(new Money(new BigDecimal("1000.5")));
  }

  @Test
  void reads_an_amount_that_publishes_no_decimals() {
    assertThat(PublishedValues.amount("60.000 sen IVE"))
        .extracting(PublishedAmount::amount)
        .isEqualTo(new Money(new BigDecimal("60000")));
  }

  /**
   * A published placeholder is not an amount. The estimated value is written {@code _} on records
   * that state none, and reading it as a figure would invent one.
   */
  @Test
  void answers_no_amount_for_text_that_is_not_numeric() {
    assertThat(PublishedValues.amount("_")).isNull();
    assertThat(PublishedValues.amount("3.378.552,09 lorem")).isNull();
    assertThat(PublishedValues.amount("   ")).isNull();
    assertThat(PublishedValues.amount(null)).isNull();
  }

  /**
   * All three widths the record writes dates in. A formatter accepting only the widest — which is
   * the one the feature's draft named — would read neither of the other two.
   */
  @Test
  void reads_each_of_the_three_widths_the_record_writes_dates_in() {
    assertThat(PublishedValues.date("08-03-2024")).isEqualTo(LocalDate.of(2024, 3, 8));
    assertThat(PublishedValues.date("08-03-2024 08:01")).isEqualTo(LocalDate.of(2024, 3, 8));
    assertThat(PublishedValues.date("04-04-2024 09:47:50")).isEqualTo(LocalDate.of(2024, 4, 4));
  }

  /**
   * The hazard the criterion names: a time-bearing value read by a lenient parse can land on the
   * next day or fall back to today. It lands on the day it published, or on nothing.
   */
  @Test
  void reads_the_time_bearing_date_as_the_day_it_published() {
    assertThat(PublishedValues.date("31-12-2024 23:59:59")).isEqualTo(LocalDate.of(2024, 12, 31));
  }

  /** A date nobody published is worse than no date, so an impossible one is refused. */
  @Test
  void answers_no_date_for_the_day_that_cannot_exist() {
    assertThat(PublishedValues.date("31-02-2024")).isNull();
    assertThat(PublishedValues.date("2024-03-08")).isNull();
    assertThat(PublishedValues.date(null)).isNull();
  }

  @Test
  void reads_the_lote_count_and_refuses_what_is_not_one() {
    assertThat(PublishedValues.count(" 2 ")).isEqualTo(2);
    assertThat(PublishedValues.count("0")).isZero();
    assertThat(PublishedValues.count("-1")).isNull();
    assertThat(PublishedValues.count("dous")).isNull();
    assertThat(PublishedValues.count(null)).isNull();
  }

  /**
   * The rule the whole of criterion 44 rests on. The ends go, and nothing inside does — a real
   * record publishes an object containing a double space, and a parse that tidied it would store
   * text the source never published.
   */
  @Test
  void trims_published_text_at_its_ends_and_nowhere_else() {
    assertThat(PublishedValues.text("\n   dentro do  programa\n   de impulso   \n"))
        .isEqualTo("dentro do  programa\n   de impulso");
  }

  @Test
  void answers_nothing_for_text_that_is_empty_once_trimmed() {
    assertThat(PublishedValues.text("   \n  ")).isNull();
    assertThat(PublishedValues.text("")).isNull();
    assertThat(PublishedValues.text(null)).isNull();
  }

  /**
   * The separator is written here as an explicit {@code  } rather than as a space, because a
   * plain space is what a hand-written fixture reaches for and it would let an implementation that
   * splits on {@code " "} pass while failing on every page the source serves.
   */
  @Test
  void separates_the_classification_code_from_the_wording_joined_to_it() {
    assertThat(PublishedValues.code("45000000 Trabajos de construcción"))
        .isEqualTo("45000000");
    assertThat(PublishedValues.description("45000000 Trabajos de construcción"))
        .isEqualTo("Trabajos de construcción");
    assertThat(PublishedValues.code("ES111 A Coruña")).isEqualTo("ES111");
    assertThat(PublishedValues.description("ES111 A Coruña")).isEqualTo("A Coruña");
  }

  @Test
  void answers_the_whole_cell_as_the_code_where_it_carries_no_wording() {
    assertThat(PublishedValues.code("  ES111  ")).isEqualTo("ES111");
    assertThat(PublishedValues.description("  ES111  ")).isNull();
  }

  @Test
  void answers_no_code_for_the_cell_that_is_empty_once_trimmed() {
    assertThat(PublishedValues.code("   ")).isNull();
    assertThat(PublishedValues.code(null)).isNull();
    assertThat(PublishedValues.description("   ")).isNull();
  }
}
