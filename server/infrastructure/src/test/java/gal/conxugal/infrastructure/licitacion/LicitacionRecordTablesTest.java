package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAward;
import gal.conxugal.domain.licitacion.PublishedCpvClassification;
import gal.conxugal.domain.licitacion.PublishedFormalisation;
import gal.conxugal.domain.licitacion.PublishedLote;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/**
 * What each table rule means, on markup small enough to state what the case is about. What the
 * real source publishes is {@link CapturedLicitacionRecordTest}'s, against the captured records
 * themselves.
 *
 * <p>Several of these cases <em>cannot</em> be captured: no fixture in the repo publishes a
 * populated {@code Relación de lotes}, a lote spelled two ways across two tables, or a table the
 * source has changed the shape of. They are the cases the design turns on, so they are written by
 * hand rather than left untested.
 */
class LicitacionRecordTablesTest {

  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");

  /** Enough of a page to be a record at all; every case below adds its tables to this. */
  private static final String RECORD =
      """
      <div class="col-md-6 text-right"><p>Estado do procedemento: <em>Formalizado</em></p></div>
      <div class="row infoConcurso"><dl><dt>Obxecto</dt><dd>Mellora</dd></dl></div>
      """;

  private static final String AWARD_HEADER =
      """
      <th><a href="#">Lote</a></th><th><a href="#">Part.</a></th>
      <th><a href="#">Resolución</a></th><th><a href="#">Adxudicatario</a></th>
      <th><a href="#">Importe</a></th><th><a href="#">Data difusión</a></th>
      <th><a href="#">Prazo de execución</a></th><th><a href="#">Recurso/ Prazo</a></th>
      """;

  private static final String FORMALISATION_HEADER =
      """
      <th>Data formalización</th><th>Lote</th><th>Contratista</th>
      <th>Nacionalidade</th><th>Importe</th><th>Data difusión</th>
      """;

  @Test
  void normalises_the_zero_padded_lote_cell_the_award_table_writes() {
    LicitacionRecord record =
        read(
            awardTable(
                awardRow(
                    "05", "3", "Adxudicado", "ACME, S.L.", "1.000,00 €", "01-02-2024", "3 m")));

    assertThat(record.awards()).extracting(PublishedAward::loteKey).containsExactly("5");
  }

  /**
   * <strong>The naive join is what this asserts against.</strong> The award table writes
   * {@code 1} and the formalisation writes {@code 01} for the same lote — measured, padding varies
   * within a single table, not merely between them — so a join on the raw cell would find no
   * formalisation for this award and demote it from the identifier the source published to no
   * identifier at all. Both cells reduce to one key, and looking the formalisation up by the
   * award's key finds the row that carries {@code B36746584}.
   *
   * <p>Whether the award then <em>holds</em> that identifier is not this task's: resolving an
   * awardee is a later one's, and this parse's job is to make the value it needs available.
   */
  @Test
  void attaches_the_formalisation_to_the_award_whose_lote_cell_is_spelled_otherwise() {
    LicitacionRecord record =
        read(
            awardTable(
                    awardRow(
                        "1", "3", "Adxudicado", "ACME, S.L.", "1.000,00 €", "01-02-2024", "3 m"))
                + formalisationTable(
                    formalisationRow(
                        "10-03-2024", "01", "ACME, S.L.<br/>B36746584", "España", "1.000,00 €")));

    PublishedAward award = record.awards().getFirst();
    assertThat(award.loteKey()).isEqualTo("1");
    assertThat(record.formalisations())
        .extracting(PublishedFormalisation::loteKey)
        .containsExactly("1")
        .doesNotContain("01");

    Optional<PublishedFormalisation> matched =
        record.formalisations().stream()
            .filter(formalisation -> Objects.equals(formalisation.loteKey(), award.loteKey()))
            .findFirst();
    assertThat(matched)
        .get()
        .extracting(PublishedFormalisation::fiscalIdentifier)
        .isEqualTo(new FiscalIdentifier("B36746584"));
  }

  /**
   * The rule the whole design exists for. Without this case the parse would pass every other test
   * here while reading its columns by index, and the day the source inserts a column it would
   * store amounts as execution periods.
   */
  @Test
  void reads_the_columns_by_their_header_when_the_source_reorders_them() {
    String reversed =
        """
        <table><thead><tr>
        <th>Recurso/ Prazo</th><th>Prazo de execución</th><th>Data difusión</th>
        <th>Importe</th><th>Adxudicatario</th><th>Resolución</th><th>Part.</th><th>Lote</th>
        </tr></thead><tbody><tr>
        <td></td><td>12 meses</td><td>01-02-2024</td><td>1.000,00 €</td>
        <td>ACME, S.L.</td><td>Adxudicado</td><td>4</td><td>2</td>
        </tr></tbody></table>
        """;

    LicitacionRecord record = read(container("consulta-resolucion", reversed));

    assertThat(record.awards())
        .extracting(
            PublishedAward::loteKey,
            PublishedAward::bidderCount,
            PublishedAward::resolution,
            PublishedAward::awardeeName,
            PublishedAward::amount,
            PublishedAward::executionPeriod)
        .containsExactly(tuple("2", 4, "Adxudicado", "ACME, S.L.", money("1000.00"), "12 meses"));
  }

  /** The documented one-line layout, which no capture in the repo publishes. */
  @Test
  void splits_the_contratista_cell_the_source_writes_on_one_line() {
    LicitacionRecord record =
        read(
            formalisationTable(
                formalisationRow(
                    "28-06-2012", "01", "EQUINSE, S.A. A41111220", "España", "25.627,12 €")));

    assertThat(record.formalisations())
        .extracting(
            PublishedFormalisation::contratistaName, PublishedFormalisation::fiscalIdentifier)
        .containsExactly(tuple("EQUINSE, S.A.", new FiscalIdentifier("A41111220")));
  }

  /**
   * One route to an identifier that did not answer is not a broken record. The row still parses,
   * and it does not go to the outstanding ledger — resolution simply falls to another route.
   */
  @Test
  void keeps_the_formalisation_whose_contratista_cell_ends_in_no_identifier() {
    LicitacionRecord record =
        read(
            formalisationTable(
                formalisationRow(
                    "28-06-2012", "_", "ANGEL CABARCOS ABADIN", "España", "3.630,00 €")));

    assertThat(record.formalisations())
        .extracting(
            PublishedFormalisation::contratistaName, PublishedFormalisation::fiscalIdentifier)
        .containsExactly(tuple("ANGEL CABARCOS ABADIN", null));
  }

  /**
   * The only route to the decoration path: every capture's {@code Relación de lotes} is
   * header-row-only, so without this the lotes table would be read by nothing.
   */
  @Test
  void decorates_the_award_table_lote_with_what_the_lotes_table_publishes() {
    LicitacionRecord record =
        read(
            awardTable(
                    awardRow(
                        "1", "3", "Adxudicado", "ACME, S.L.", "1.000,00 €", "01-02-2024", "3 m"))
                + lotesTable("<tr><td>1</td><td>Obra civil</td><td>900,00 €</td></tr>"));

    assertThat(record.lotes())
        .extracting(
            PublishedLote::identifier, PublishedLote::description, PublishedLote::estimatedValue)
        .containsExactly(tuple("1", "Obra civil", money("900.00")));
  }

  /**
   * A lote is stored under the spelling the awarding table published, so the tables are read
   * award-first and the first spelling met wins. {@link gal.conxugal.domain.licitacion.LoteKey} is
   * for comparison and never for storage, which is what lets the two spellings meet at all.
   */
  @Test
  void holds_the_lote_spelling_the_award_table_published_rather_than_the_lotes_table_one() {
    LicitacionRecord record =
        read(
            awardTable(
                    awardRow(
                        "05", "3", "Adxudicado", "ACME, S.L.", "1.000,00 €", "01-02-2024", "3 m"))
                + lotesTable("<tr><td>5</td><td>Obra civil</td><td></td></tr>"));

    assertThat(record.lotes())
        .extracting(PublishedLote::identifier, PublishedLote::description)
        .containsExactly(tuple("05", "Obra civil"));
  }

  /**
   * A formalisation naming a lote no award row does would otherwise have no lote to hang on, and
   * the row would be lost at the point it is stored. The lotes table alone is not enough either —
   * that is 822054's case — so a lote is every lote any table names.
   */
  @Test
  void yields_the_lote_the_formalisation_names_where_the_award_table_names_none() {
    LicitacionRecord record =
        read(
            formalisationTable(
                formalisationRow("10-03-2024", "3", "ACME, S.L. B36746584", "España", "1,00 €")));

    assertThat(record.lotes()).extracting(PublishedLote::identifier).containsExactly("3");
  }

  @Test
  void parses_the_record_that_publishes_none_of_the_five_tables() {
    LicitacionRecord record = read("");

    assertThat(record.lotes()).isEmpty();
    assertThat(record.awards()).isEmpty();
    assertThat(record.formalisations()).isEmpty();
    assertThat(record.cpvClassifications()).isEmpty();
    assertThat(record.nutClassifications()).isEmpty();
  }

  /**
   * A panel published without its table states nothing, and reading more into it would be an
   * inference. Stated as a behaviour so it cannot be tightened by accident: the cost of guessing
   * wrong here is a real procedure in the outstanding ledger.
   */
  @Test
  void parses_the_section_the_source_published_without_any_table() {
    LicitacionRecord record = read(container("consulta-resolucion", "<p>Non hai datos</p>"));

    assertThat(record.awards()).isEmpty();
  }

  /**
   * The other half of the same judgement. A table that is there and can no longer be read means
   * the parse has stopped understanding the source, and a procedure stored from it is stored as
   * authoritative with nothing ever returning to it.
   */
  @Test
  void throws_when_the_resolution_table_publishes_no_awardee_column() {
    String withoutAwardee = AWARD_HEADER.replace("<th><a href=\"#\">Adxudicatario</a></th>", "");

    assertThatThrownBy(
            () ->
                read(
                    container(
                        "consulta-resolucion",
                        table(withoutAwardee, "<tr><td>1</td></tr>"))))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("Adxudicatario");
  }

  /** It cannot be read by label, and reading it by index is what this design forbids. */
  @Test
  void throws_when_the_resolution_table_publishes_no_header_row() {
    assertThatThrownBy(
            () ->
                read(
                    container(
                        "consulta-resolucion",
                        "<table><tbody><tr><td>1</td></tr></tbody></table>")))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("Lote");
  }

  @Test
  void throws_when_the_formalisation_row_is_too_short_to_read() {
    assertThatThrownBy(
            () ->
                read(
                    container(
                        "consulta-formalizacion",
                        table(
                            FORMALISATION_HEADER,
                            "<tr><td>10-03-2024</td><td>1</td><td>ACME</td></tr>"))))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("too few");
  }

  /** Nothing may be refused over a column this parse never reads. */
  @Test
  void ignores_the_column_this_parse_never_reads() {
    String withoutRecurso =
        AWARD_HEADER.replace("<th><a href=\"#\">Recurso/ Prazo</a></th>", "");
    String row =
        """
        <tr><td>1</td><td>3</td><td>Adxudicado</td><td>ACME, S.L.</td>
        <td>1.000,00 €</td><td>01-02-2024</td><td>3 meses</td></tr>
        """;

    LicitacionRecord record =
        read(container("consulta-resolucion", table(withoutRecurso, row)));

    assertThat(record.awards()).extracting(PublishedAward::loteKey).containsExactly("1");
  }

  /** There is no row to make, and it is not a structural failure of the table. */
  @Test
  void skips_the_classification_row_whose_code_cell_is_empty() {
    String rows =
        """
        <tr><td>   </td><td>_</td><td>01-02-2024</td></tr>
        <tr><td>45000000&nbsp;Trabajos</td><td>_</td><td>01-02-2024</td></tr>
        """;

    LicitacionRecord record =
        read(
            container(
                "collapseCPV",
                table("<th>código CPV</th><th>Lote</th><th>Data difusión</th>", rows)));

    assertThat(record.cpvClassifications())
        .extracting(PublishedCpvClassification::code)
        .containsExactly("45000000");
  }

  /**
   * A value that cannot be interpreted is absent and the row stands — the failures worth losing a
   * whole record over are structural, and a price the source wrote as prose is not one.
   */
  @Test
  void answers_nothing_for_the_award_amount_the_source_published_unreadably() {
    LicitacionRecord record =
        read(
            awardTable(
                awardRow("_", "3", "Adxudicado", "ACME, S.L.", "n/d", "vai saber", "3 meses")));

    assertThat(record.awards())
        .extracting(
            PublishedAward::amount, PublishedAward::resolutionDate, PublishedAward::awardeeName)
        .containsExactly(tuple(null, null, "ACME, S.L."));
  }

  private static LicitacionRecord read(String tables) {
    return LicitacionRecordDocument.read(
        PUBLICATION_ID, Jsoup.parse(RECORD + tables, "/licitacion"));
  }

  private static String container(String id, String body) {
    return "<div class=\"tab-pane fade\" id=\"%s\">%s</div>".formatted(id, body);
  }

  private static String table(String header, String rows) {
    return "<table class=\"table\"><thead><tr>%s</tr></thead><tbody>%s</tbody></table>"
        .formatted(header, rows);
  }

  private static String awardTable(String rows) {
    return container("consulta-resolucion", table(AWARD_HEADER, rows));
  }

  private static String awardRow(
      String lote,
      String participants,
      String resolution,
      String awardee,
      String amount,
      String diffusion,
      String period) {
    // The empty anchors and spans are the source's own: the count and the resolution label each
    // sit beside a glyphicon link, and the diffusion date beside a popover carrying no text.
    String row =
        """
        <tr><td>%s</td><td>%s<a href="#"><span class="glyphicon"></span></a></td>
        <td><a href="#"><span class="glyphicon"></span></a><span title="%s">%s</span></td>
        <td>%s</td><td class="text-right">%s</td>
        <td><a class="tooltip-cpg">%s</a><a class="popover-html"><span></span></a></td>
        <td>%s</td><td></td></tr>
        """;
    return row.formatted(
        lote, participants, resolution, resolution, awardee, amount, diffusion, period);
  }

  private static String formalisationTable(String rows) {
    return container("consulta-formalizacion", table(FORMALISATION_HEADER, rows));
  }

  private static String formalisationRow(
      String date, String lote, String contratista, String nationality, String amount) {
    // The hidden input really is in the date cell, and contributes nothing to its text.
    String row =
        """
        <tr><td>%s<br><input type="hidden" value="" /></td><td>%s</td><td>%s</td>
        <td>%s</td><td class="text-right">%s</td>
        <td><a class="tooltip-cpg">31-07-2024 08:03:32</a></td></tr>
        """;
    return row.formatted(date, lote, contratista, nationality, amount);
  }

  private static String lotesTable(String rows) {
    return container(
        "ventanaModalLotes",
        table("<th>Lote</th><th>Descrición</th><th>Valor estimado</th>", rows));
  }

  private static Money money(String value) {
    return new Money(new BigDecimal(value));
  }
}
