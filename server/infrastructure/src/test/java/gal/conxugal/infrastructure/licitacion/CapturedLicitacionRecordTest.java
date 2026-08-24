package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAward;
import gal.conxugal.domain.licitacion.PublishedCpvClassification;
import gal.conxugal.domain.licitacion.PublishedFormalisation;
import gal.conxugal.domain.licitacion.PublishedLote;
import gal.conxugal.domain.licitacion.PublishedNutClassification;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * The table parse against pages the source really served. No HTTP and no database — the captures
 * are read straight off the classpath — which is what lets the cases the source's own shape
 * decides be asserted here rather than only where a container is needed.
 *
 * <p><strong>Captures rather than hand-written markup, for these cases specifically.</strong> Each
 * one turns on something a tidy fixture would not have: a code joined to its wording by a
 * non-breaking space, a contratista split across a {@code <br>}, a count sitting beside a link,
 * twelve classification rows every one of which is procedure-wide on a procedure that has two
 * lotes. What each rule <em>means</em>, on markup small enough to state it, is
 * {@link LicitacionRecordTablesTest}'s.
 *
 * <p>The charset is named rather than defaulted: the column headers this parse looks values up by
 * are accented — {@code Resolución}, {@code Data difusión}, {@code Descrición}, {@code código CPV},
 * {@code Nacionalidade} — so decoding as UTF-8 would not merely mangle text, it would find no
 * columns at all.
 */
class CapturedLicitacionRecordTest {

  /** Two lotes, two awards, two formalisations, twelve CPV rows — four of the measured cases. */
  private static final String WITH_LOTES = "822054";

  /** Lotless, and formalised: one award and one formalisation, both against the procedure. */
  private static final String LOTLESS = "828959";

  /**
   * Not a licitación at all. The same endpoint serves both families from one identifier space, and
   * this one publishes no formalisation and neither classification table.
   */
  private static final String CONTRATO_MENOR = "2001090-contrato-menor";

  @Test
  void reads_both_awards_the_two_lote_procedure_publishes() {
    LicitacionRecord record = read(WITH_LOTES);

    assertThat(record.awards())
        .extracting(
            PublishedAward::loteKey,
            PublishedAward::resolution,
            PublishedAward::diffusionDate,
            PublishedAward::executionPeriod,
            PublishedAward::awardeeName)
        .containsExactly(
            tuple(
                "1",
                "Adxudicado",
                LocalDate.of(2024, 7, 10),
                "12 meses",
                "XESTION AMBIENTAL DE CONTRATAS, S.L."),
            tuple("2", "Adxudicado", LocalDate.of(2024, 7, 10), "2 meses 7 días", "ESQUEIRO, SL"));
  }

  /**
   * The half of the invariant the model cannot enforce. {@code Award}'s lote is nullable so that a
   * lotless procedure can hang its award somewhere, which also makes a procedure-level row on a
   * lotted procedure expressible — nothing but this parse stops one being written.
   */
  @Test
  void yields_no_procedure_level_award_where_every_row_names_its_lote() {
    LicitacionRecord record = read(WITH_LOTES);

    assertThat(record.awards()).extracting(PublishedAward::loteKey).doesNotContainNull();
  }

  /**
   * The count is published in the resolution table, so it is read here rather than where the
   * bidder list is parsed — re-reading this table for one column would duplicate the whole parse.
   * It sits beside the key the cross-check joins on.
   */
  @Test
  void exposes_the_participant_count_beside_the_lote_key_it_belongs_to() {
    LicitacionRecord record = read(WITH_LOTES);

    assertThat(record.awards())
        .extracting(PublishedAward::loteKey, PublishedAward::bidderCount)
        .containsExactly(tuple("1", 10), tuple("2", 7));
  }

  /**
   * <strong>The awarded amount comes from the resolution table and from nowhere else.</strong>
   * This procedure's {@code Orzamento base de licitación} is {@code 3.378.552,09}, which is the
   * same figure its listing row publishes as {@code importe} — so a parse that took the budget for
   * the award would fill every total with budgets, silently and plausibly. Both figures are read
   * from the one document, so this compares two parsed values rather than one against a literal.
   */
  @Test
  void reads_the_awarded_amounts_and_not_the_budget_the_same_record_publishes() {
    LicitacionRecord record = read(WITH_LOTES);

    Money baseBudget = Objects.requireNonNull(record.baseBudget()).amount();
    assertThat(baseBudget).isEqualTo(money("3378552.09"));
    assertThat(record.awards())
        .extracting(PublishedAward::amount)
        .containsExactly(money("3052743.72"), money("206996.66"))
        .doesNotContain(baseBudget);
  }

  /**
   * The formalisation is the primary route to an awardee's identifier — 58% of all award rows —
   * and on this capture the name and the identifier are on either side of a {@code <br>}.
   */
  @Test
  void reads_the_identifier_the_formalisation_publishes_for_each_lote() {
    LicitacionRecord record = read(WITH_LOTES);

    assertThat(record.formalisations())
        .extracting(
            PublishedFormalisation::loteKey,
            PublishedFormalisation::formalisationDate,
            PublishedFormalisation::contratistaName,
            PublishedFormalisation::fiscalIdentifier,
            PublishedFormalisation::nationality,
            PublishedFormalisation::amount)
        .containsExactly(
            tuple(
                "1",
                LocalDate.of(2024, 7, 29),
                "XESTION AMBIENTAL DE CONTRATAS, S.L.",
                new FiscalIdentifier("B36746584"),
                "España",
                money("3052743.72")),
            tuple(
                "2",
                LocalDate.of(2024, 7, 30),
                "ESQUEIRO, SL",
                new FiscalIdentifier("B15590581"),
                "España",
                money("206996.66")));
  }

  /**
   * The departure amendment 2 legitimises, and this is the procedure it was measured on: two
   * lotes, two separate awards, and every one of thirteen classification rows carrying the
   * procedure-wide marker. A model requiring a lote on a classification row could not store it.
   */
  @Test
  void yields_no_lote_reference_for_the_classifications_this_record_publishes_procedure_wide() {
    LicitacionRecord record = read(WITH_LOTES);

    assertThat(record.lotes()).hasSize(2);
    assertThat(record.cpvClassifications())
        .hasSize(12)
        .extracting(PublishedCpvClassification::loteKey)
        .containsOnlyNulls();
    assertThat(record.nutClassifications())
        .extracting(PublishedNutClassification::loteKey)
        .containsOnlyNulls();
  }

  /**
   * The code names an entry in a regulated list, and the source publishes the list's own wording
   * beside it in the same cell, behind a non-breaking space. A parse taking the whole cell would
   * key the vocabulary on code-and-wording; one splitting on a plain space would not split at all.
   */
  @Test
  void separates_the_code_from_the_wording_the_source_joins_it_to() {
    LicitacionRecord record = read(WITH_LOTES);

    assertThat(record.cpvClassifications())
        .first()
        .extracting(
            PublishedCpvClassification::code,
            PublishedCpvClassification::description,
            PublishedCpvClassification::diffusionDate)
        .containsExactly("45000000", "Trabajos de construcción", LocalDate.of(2024, 3, 8));
    assertThat(record.nutClassifications())
        .first()
        .extracting(PublishedNutClassification::code, PublishedNutClassification::description)
        .containsExactly("ES111", "A Coruña");
  }

  /**
   * This procedure's {@code Relación de lotes} is header-row-only while {@code Nº lotes} says two
   * and the award table names both. A parse that discovered lotes from the lotes table would have
   * found none here — and lost both awards with them.
   */
  @Test
  void yields_the_two_lotes_the_award_table_names_where_the_lotes_table_is_empty() {
    LicitacionRecord record = read(WITH_LOTES);

    assertThat(record.loteCount()).isEqualTo(2);
    assertThat(record.lotes())
        .extracting(
            PublishedLote::identifier, PublishedLote::description, PublishedLote::estimatedValue)
        .containsExactly(tuple("1", null, null), tuple("2", null, null));
  }

  /** The plain case: 85 of 100 measured procedures have no lotes, and the cell reads {@code _}. */
  @Test
  void reads_the_lotless_award_and_its_formalisation_against_the_procedure() {
    LicitacionRecord record = read(LOTLESS);

    assertThat(record.lotes()).isEmpty();
    assertThat(record.awards())
        .extracting(
            PublishedAward::loteKey, PublishedAward::amount, PublishedAward::awardeeName)
        .containsExactly(tuple(null, money("55000.00"), "LA DÉCIMA PLANTA, S.L."));
    assertThat(record.formalisations())
        .extracting(
            PublishedFormalisation::loteKey, PublishedFormalisation::fiscalIdentifier)
        .containsExactly(tuple(null, new FiscalIdentifier("B75928697")));
  }

  /**
   * The second real classification shape the repo owns, and the only code in any capture that is
   * not five characters: {@code ES} is the country-level NUTS entry, published beside a regional
   * one on the same procedure. Pinned because it is what any future tightening of the code split
   * towards a fixed width would silently reject.
   */
  @Test
  void reads_the_country_level_entry_the_lotless_record_classifies_itself_with() {
    LicitacionRecord record = read(LOTLESS);

    assertThat(record.cpvClassifications())
        .extracting(PublishedCpvClassification::code, PublishedCpvClassification::description)
        .containsExactly(tuple("79341000", "Servicios de publicidad"));
    assertThat(record.nutClassifications())
        .extracting(PublishedNutClassification::code, PublishedNutClassification::description)
        .containsExactly(tuple("ES113", "Ourense"), tuple("ES", "España"));
  }

  /**
   * Absence is an ordinary answer, and this is what it looks like on a real page. Every
   * <em>En curso</em>, <em>Pendente de adxudicar</em>, <em>Deserto</em>, <em>Anulado</em> and
   * <em>Renuncia</em> procedure publishes no resolution table either — 278 of the ~2 000 listing
   * rows sampled — so a parse that refused a record over a table it does not publish would send
   * every one of them to the outstanding ledger.
   */
  @Test
  void parses_the_contrato_menor_that_publishes_none_of_the_three_tables() {
    LicitacionRecord record = read(CONTRATO_MENOR);

    assertThat(record.formalisations()).isEmpty();
    assertThat(record.cpvClassifications()).isEmpty();
    assertThat(record.nutClassifications()).isEmpty();
    assertThat(record.awards())
        .extracting(
            PublishedAward::loteKey, PublishedAward::amount, PublishedAward::bidderCount)
        .containsExactly(tuple(null, money("3630.00"), 1));
  }

  private static LicitacionRecord read(String publicationId) {
    return LicitacionRecordDocument.read(
        new PublicationId(publicationId), capture(publicationId + ".html"));
  }

  private static Document capture(String name) {
    try (InputStream page =
        CapturedLicitacionRecordTest.class.getResourceAsStream("/licitacion/" + name)) {
      String absent = "Captured record %s is not on the classpath".formatted(name);
      return Jsoup.parse(
          Objects.requireNonNull(page, absent), StandardCharsets.ISO_8859_1.name(), "/licitacion");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@link Money} compares its scale, so a figure is written here as the source published it. */
  private static Money money(String value) {
    return new Money(new BigDecimal(value));
  }
}
