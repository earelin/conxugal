package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedBidder;
import gal.conxugal.domain.licitacion.PublishedConsortiumMember;
import gal.conxugal.domain.licitacion.PublishedLote;
import gal.conxugal.domain.operador.FiscalIdentifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the bidder rules mean, on markup small enough to state what each case is about. What the
 * real source publishes is {@link CapturedLicitacionRecordTest}'s, against the captured records
 * themselves.
 *
 * <p>Most of these cases <em>cannot</em> be captured. No fixture in the repo publishes a consortium
 * whose name does not begin {@code UTE}, a consortium carrying its own {@code U…} identifier, a
 * {@code TEMP-…} placeholder, a bidder count that disagrees with its rows, or a procedure with
 * award rows and no bidder table at all. Every one of them is a case the design turns on, so they
 * are written by hand rather than left untested.
 */
class LicitacionRecordBiddersTest {

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

  private static final String BIDDER_HEADER =
      "<th scope=\"col\">Lote</th><th scope=\"col\">NIF</th><th scope=\"col\">Nome</th>";

  // --- the classification: markup, never the name and never the identifier ----------------------

  /**
   * <strong>The case a name test gets wrong.</strong> 7 of the 35 measured consortia are published
   * under a name that does not begin {@code UTE}, and this is one of them. A name-prefix test would
   * record {@code MISTURAS-INGESAN} as a single firm bidding under the placeholder identifier
   * {@code -}, which is exactly what this asserts against.
   */
  @Test
  void recognises_the_consortium_whose_name_does_not_begin_with_ute() {
    LicitacionRecord record =
        read(
            bidderTable(
                bidderRow(
                    "1",
                    "-",
                    consortiumCell(
                        "MISTURAS-INGESAN",
                        "A32118705 - MISTURAS OBRAS E PROXECTOS, S.A.",
                        "A28517308 - INGENIERIA Y SERVICIOS DEL NOROESTE, S.A."))));

    assertThat(record.bidders())
        .singleElement()
        .isInstanceOf(PublishedBidder.Consortium.class)
        .extracting(PublishedBidder::name)
        .isEqualTo("MISTURAS-INGESAN");
    assertThat(membersOf(record))
        .extracting(PublishedConsortiumMember::fiscalIdentifier, PublishedConsortiumMember::name)
        .containsExactly(
            tuple(new FiscalIdentifier("A32118705"), "MISTURAS OBRAS E PROXECTOS, S.A."),
            tuple(
                new FiscalIdentifier("A28517308"),
                "INGENIERIA Y SERVICIOS DEL NOROESTE, S.A."));
  }

  /**
   * <strong>The branch is taken before the NIF cell is read, so what that cell holds cannot change
   * the answer.</strong> All three shapes occur: {@code -} and {@code TEMP-…} on 33 of the 35
   * measured consortium rows, a real {@code U…} on the other 2. None of them yields a single firm,
   * and none of them offers its cell as a firm's identifier — which is what would otherwise
   * catalogue one operador holding {@code -} for dozens of unrelated consortia.
   */
  @ParameterizedTest
  @ValueSource(strings = {"-", "TEMP-00934", "U86486669", ""})
  void yields_no_single_firm_bidder_whatever_the_consortium_nif_cell_holds(String nif) {
    LicitacionRecord record =
        read(bidderTable(bidderRow("1", nif, consortiumCell("UTE ACME-BETA", "A32118705 - ACME"))));

    assertThat(record.bidders())
        .singleElement()
        .isInstanceOf(PublishedBidder.Consortium.class);
  }

  /**
   * The 33-of-35 case. A placeholder is not the consortium's identifier either: minting a catalogue
   * identity from {@code -} or {@code TEMP-00934} pools unrelated consortia under one operador,
   * which is the same harm as offering it as a firm's and is refused on shape alone.
   */
  @ParameterizedTest
  @ValueSource(strings = {"-", "TEMP-00934", ""})
  void carries_no_identifier_for_the_consortium_the_source_declines_to_identify(String nif) {
    LicitacionRecord record =
        read(bidderTable(bidderRow("1", nif, consortiumCell("UTE ACME-BETA", "A32118705 - ACME"))));

    assertThat(consortiumOf(record).fiscalIdentifier()).isNull();
  }

  /**
   * The 2-of-35 case, and the field that makes it a different fact. The identifier is the
   * <em>consortium's own</em> rather than a bidding firm's — a distinction the sealed pair carries
   * and a boolean flag could not.
   */
  @Test
  void yields_the_published_identifier_as_the_consortium_own_where_the_row_carries_one() {
    LicitacionRecord record =
        read(
            bidderTable(
                bidderRow("1", "U86486669", consortiumCell("UTE ACME-BETA", "A32118705 - ACME"))));

    assertThat(consortiumOf(record).fiscalIdentifier())
        .isEqualTo(new FiscalIdentifier("U86486669"));
  }

  /**
   * The other 578 of 613. Every shape measured on a single-firm row is here — a trailing
   * {@code S.A.}, an internal double space, an accent, a personal NIF, a name that is fourteen
   * letters long — and not one of them nests a list, so not one of them is a consortium.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "SOCIEDAD ANONIMA DE OBRAS Y SERVICIOS COPASA",
        "CIVIS GLOBAL  S L",
        "OBRAS Y SERVICIOS GÓMEZ CRESPO, S.L.",
        "ANGEL CABARCOS ABADIN",
        "CONSTRUCCIONES",
        "UTE PRACE-TABOADA RAMOS"
      })
  void classifies_the_row_that_nests_no_list_as_one_firm(String name) {
    LicitacionRecord record =
        read(bidderTable(bidderRow("1", "B36746584", firmCell(name))));

    assertThat(record.bidders())
        .singleElement()
        .isInstanceOf(PublishedBidder.SingleFirm.class)
        .extracting(PublishedBidder::name)
        .isEqualTo(name);
  }

  /**
   * <strong>A name beginning {@code UTE} is not the test, and this is the case that says
   * so.</strong> The last row above nests nothing while calling itself a UTE, and it is read as a
   * single firm — because the markup is the publication and the name is not.
   */
  @Test
  void reads_the_ute_named_row_that_nests_no_list_as_one_firm_holding_its_identifier() {
    LicitacionRecord record =
        read(
            bidderTable(
                bidderRow("1", "U86486669", firmCell("UTE PRACE-TABOADA RAMOS"))));

    PublishedBidder bidder = record.bidders().getFirst();
    assertThat(bidder).isInstanceOf(PublishedBidder.SingleFirm.class);
    assertThat(((PublishedBidder.SingleFirm) bidder).fiscalIdentifier())
        .isEqualTo(new FiscalIdentifier("U86486669"));
  }

  // --- the member entries ----------------------------------------------------------------------

  /**
   * A member entry that does not split yields the whole entry as a name and no identifier, on the
   * failure direction every identifier judgement on this record takes: a member that resolves to
   * nobody costs one membership, whereas a member resolved to the wrong operador corrupts the
   * catalogue. The hyphen must be spaced on both sides — company names carry bare ones, and an
   * unspaced split would file {@code MISTURAS-INGESAN} under the head {@code MISTURAS}.
   */
  @ParameterizedTest
  @ValueSource(strings = {"MISTURAS-INGESAN", "ACME - Servicios", "TEMP-00934 - ACME", "ACME"})
  void holds_the_whole_entry_as_the_member_name_where_it_does_not_split(String entry) {
    LicitacionRecord record =
        read(bidderTable(bidderRow("1", "-", consortiumCell("UTE ACME-BETA", entry))));

    assertThat(membersOf(record))
        .extracting(PublishedConsortiumMember::name, PublishedConsortiumMember::fiscalIdentifier)
        .containsExactly(tuple(entry, null));
  }

  /**
   * The consortium's name is the outer list's own item. Read as the cell's text instead, it would
   * name all three parties at once and no lookup for the consortium would ever match it.
   */
  @Test
  void keeps_the_consortium_name_clear_of_the_members_nested_beneath_it() {
    LicitacionRecord record =
        read(
            bidderTable(
                bidderRow(
                    "1",
                    "-",
                    consortiumCell(
                        "UTE ACME-BETA", "A32118705 - ACME", "B36746584 - BETA"))));

    assertThat(consortiumOf(record).name()).isEqualTo("UTE ACME-BETA");
  }

  /**
   * <strong>The same consortium written as valid HTML.</strong> The source closes the name's
   * {@code </li>} before opening the member list, which leaves that list a sibling rather than a
   * child — so a classification matching only what the source emits would depend on a defect in the
   * publisher's template.
   *
   * <p>What it would cost is the whole point of the branch: corrected upstream, every consortium
   * would become a single firm holding the {@code -} its NIF cell carries, with its members gone
   * and nothing raising — the row count the {@code Part.} cross-check compares is unchanged, and
   * the name still reads plausibly. Silent, permanent, and exactly the harm this branch exists to
   * prevent.
   */
  @Test
  void recognises_the_consortium_whose_member_list_is_nested_as_valid_markup() {
    String wellFormed =
        "<ul class='list-unstyled'><li>UTE ACME-BETA"
            + "<ul><li>A32118705 - ACME</li><li>B36746584 - BETA</li></ul></li></ul>";

    LicitacionRecord record = read(bidderTable(bidderRow("1", "-", wellFormed)));

    assertThat(consortiumOf(record).name()).isEqualTo("UTE ACME-BETA");
    assertThat(membersOf(record))
        .extracting(PublishedConsortiumMember::fiscalIdentifier, PublishedConsortiumMember::name)
        .containsExactly(
            tuple(new FiscalIdentifier("A32118705"), "ACME"),
            tuple(new FiscalIdentifier("B36746584"), "BETA"));
  }

  /**
   * <strong>A name the source wraps in an inline element is still a name.</strong> This page wraps
   * text in {@code <span class="tooltip-cpg">} freely — the {@code Resolución} and {@code Part.}
   * cells both do — so a bidder cell that starts doing the same is not far-fetched. Read as the
   * item's own text it would answer nothing at all, and a consortium the source declines to
   * identify is found by its name and by nothing else.
   */
  @Test
  void keeps_the_bidder_name_the_source_wraps_in_an_inline_element() {
    LicitacionRecord record =
        read(
            bidderTable(
                bidderRow(
                    "1",
                    "B36746584",
                    "<ul class='list-unstyled'><li><span>ACME, S.L.</span></li></ul>")));

    assertThat(record.bidders())
        .singleElement()
        .isInstanceOf(PublishedBidder.SingleFirm.class)
        .extracting(PublishedBidder::name)
        .isEqualTo("ACME, S.L.");
  }

  /**
   * Every captured row wraps its name in a list, so this is the shape the source has not been seen
   * publishing — and the one a template change could produce tomorrow. The whole cell is the name
   * and the row is still a bidder, rather than a party the record published and the parse dropped.
   */
  @Test
  void reads_the_whole_cell_as_the_name_where_the_source_wraps_it_in_no_list() {
    LicitacionRecord record =
        read(bidderTable(bidderRow("1", "B36746584", "ACME, S.L.")));

    assertThat(record.bidders())
        .singleElement()
        .isInstanceOf(PublishedBidder.SingleFirm.class)
        .extracting(PublishedBidder::name)
        .isEqualTo("ACME, S.L.");
  }

  /** A cell the source published empty names nobody, and is not a reason to refuse the record. */
  @Test
  void answers_no_name_for_the_bidder_cell_the_source_published_empty() {
    LicitacionRecord record = read(bidderTable(bidderRow("1", "B36746584", "")));

    assertThat(record.bidders())
        .singleElement()
        .extracting(PublishedBidder::name)
        .isNull();
  }

  // --- the Part. cross-check -------------------------------------------------------------------

  /**
   * <strong>A short bidder list is indistinguishable from a genuine one</strong>, and would
   * understate competition for ever. The award table says ten participants and the bidder table
   * publishes nine, so the procedure is refused and goes to the outstanding ledger rather than
   * being stored.
   */
  @Test
  void refuses_the_record_whose_bidder_rows_fall_short_of_the_participant_count() {
    assertThatThrownBy(
            () ->
                read(
                    awardTable(ordinaryAwardOfLote("1", "10"))
                        + bidderTable(firmRowsOfLote("1", 9))))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("822054")
        .hasMessageContaining("lote 1");
  }

  /** The same failure in the other direction: a row too many is as wrong as a row too few. */
  @Test
  void refuses_the_record_that_publishes_more_bidder_rows_than_participants() {
    assertThatThrownBy(
            () ->
                read(
                    awardTable(ordinaryAwardOfLote("1", "2"))
                        + bidderTable(firmRowsOfLote("1", 3))))
        .isInstanceOf(LicitacionRecordUnavailableException.class);
  }

  @Test
  void yields_the_bidder_list_where_the_participant_count_agrees_with_it() {
    LicitacionRecord record =
        read(awardTable(ordinaryAwardOfLote("1", "3")) + bidderTable(firmRowsOfLote("1", 3)));

    assertThat(record.bidders()).hasSize(3);
  }

  /**
   * <strong>The join is on the reduced key.</strong> The award row writes {@code _} and the bidder
   * rows write {@code -} for the same procedure-wide award point, and the award's lote is spelled
   * {@code 05} where the bidder rows spell it {@code 5}. Compared raw, neither would meet its
   * counterpart and both procedures would be refused — measured over 240 procedures, the raw join
   * disagrees on 95 of 158 award rows and every failure is an artefact of exactly these two shapes.
   */
  @Test
  void agrees_where_the_two_tables_spell_the_same_award_point_differently() {
    LicitacionRecord procedureWide =
        read(awardTable(ordinaryAwardOfLote("_", "2")) + bidderTable(firmRowsOfLote("-", 2)));
    LicitacionRecord zeroPadded =
        read(awardTable(ordinaryAwardOfLote("05", "2")) + bidderTable(firmRowsOfLote("5", 2)));

    assertThat(procedureWide.bidders()).hasSize(2);
    assertThat(zeroPadded.bidders()).hasSize(2);
  }

  /**
   * <strong>The precedence rule, stated because the two rules otherwise disagree in
   * silence.</strong> 26 of the first 70 procedures sampled published no bidder table at all —
   * open, pending, deserted or withdrawn — and nothing measured rules out an award row carrying a
   * non-zero count on such a page. An absent table is an empty list whatever {@code Part.} says,
   * and never a refusal.
   */
  @Test
  void yields_no_bidder_and_does_not_raise_where_the_record_publishes_no_bidder_table() {
    LicitacionRecord record = read(awardTable(ordinaryAwardOfLote("1", "3")));

    assertThat(record.bidders()).isEmpty();
  }

  /** The same rule where the container is published but holds no rows at all. */
  @Test
  void yields_no_bidder_and_does_not_raise_where_the_published_table_holds_no_rows() {
    LicitacionRecord record =
        read(awardTable(ordinaryAwardOfLote("1", "3")) + bidderTable(""));

    assertThat(record.bidders()).isEmpty();
  }

  /**
   * The cost of the precedence rule, pinned rather than left to be discovered. A procedure whose
   * bidder table names lote 1 and says nothing about lote 2 is stored: lote 2's count is unchecked
   * because its bidders were never published, which is the same answer the source's own per-lote
   * modal gives.
   */
  @Test
  void leaves_unchecked_the_lote_whose_bidders_the_published_table_does_not_name() {
    LicitacionRecord record =
        read(
            awardTable(ordinaryAwardOfLote("1", "2") + ordinaryAwardOfLote("2", "4"))
                + bidderTable(firmRowsOfLote("1", 2)));

    assertThat(record.bidders()).hasSize(2);
  }

  // --- the table itself ------------------------------------------------------------------------

  /**
   * The rule the whole table design exists for. Without this case the parse would pass every other
   * test here while reading its columns by index, and the day the source inserts a column — as it
   * already does, publishing a fourth {@code NUT} on 828959 — it would read names as identifiers.
   */
  @Test
  void reads_the_bidder_columns_by_their_header_when_the_source_reorders_them() {
    String reversed =
        """
        <table><thead><tr><th>Nome</th><th>NIF</th><th>Lote</th></tr></thead>
        <tbody><tr><td><ul class='list-unstyled'><li>ACME, S.L.</li></ul></td>
        <td>B36746584</td><td>1</td></tr></tbody></table>
        """;

    LicitacionRecord record = read(container("ventanaModalLicitadores", reversed));

    assertThat(record.bidders())
        .extracting(PublishedBidder::loteKey, PublishedBidder::name)
        .containsExactly(tuple("1", "ACME, S.L."));
  }

  /**
   * A table that is there and cannot be read means the parse has stopped understanding the source,
   * and a procedure stored from that is stored as authoritative with nothing ever returning to it.
   */
  @Test
  void refuses_the_bidder_table_that_publishes_no_name_column() {
    assertThatThrownBy(
            () ->
                read(
                    container(
                        "ventanaModalLicitadores",
                        table("<th>Lote</th><th>NIF</th>", "<tr><td>1</td><td>B1</td></tr>"))))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("Nome");
  }

  /**
   * <strong>The sixth table names lotes too.</strong> A procedure that publishes bidders before it
   * publishes any award has no other table for its lotes to be discovered from, and a bidder
   * hanging on a lote the record does not list is one the store cannot file.
   */
  @Test
  void names_the_lote_that_only_the_bidder_rows_publish() {
    LicitacionRecord record = read(bidderTable(firmRowsOfLote("3", 1)));

    assertThat(record.lotes())
        .extracting(PublishedLote::identifier)
        .containsExactly("3");
  }

  /**
   * The award table is read first, so a lote it spells {@code 05} keeps that spelling even where
   * the bidder rows spell it {@code 5} — the same rule every other table on the record obeys.
   */
  @Test
  void keeps_the_lote_spelling_the_award_table_published_over_the_bidder_rows() {
    LicitacionRecord record =
        read(awardTable(ordinaryAwardOfLote("05", "1")) + bidderTable(firmRowsOfLote("5", 1)));

    assertThat(record.lotes())
        .extracting(PublishedLote::identifier)
        .containsExactly("05");
  }

  /**
   * The record's single bidder, asserted to be a consortium before it is read as one — so a
   * regression in the structural branch fails as the classification it is, rather than as a cast
   * inside a helper that names none of the case it was serving.
   */
  private static PublishedBidder.Consortium consortiumOf(LicitacionRecord record) {
    return assertThat(record.bidders())
        .singleElement()
        .asInstanceOf(type(PublishedBidder.Consortium.class))
        .actual();
  }

  private static List<PublishedConsortiumMember> membersOf(LicitacionRecord record) {
    return consortiumOf(record).members();
  }

  private static LicitacionRecord read(String tables) {
    return LicitacionRecordDocument.read(
        PUBLICATION_ID, Jsoup.parse(RECORD + tables, "/licitacion"));
  }

  private static String container(String id, String body) {
    return "<div class=\"modal fade\" id=\"%s\">%s</div>".formatted(id, body);
  }

  private static String table(String header, String rows) {
    return "<table class=\"table\"><thead><tr>%s</tr></thead><tbody>%s</tbody></table>"
        .formatted(header, rows);
  }

  private static String bidderTable(String rows) {
    return container("ventanaModalLicitadores", table(BIDDER_HEADER, rows));
  }

  private static String bidderRow(String lote, String nif, String nameCell) {
    // The row classes are the source's own: the suffix is not the lote — 828959 writes
    // filaLic_0_1756774 against a lote cell of `-` — so only the Lote column is read.
    String row =
        """
        <tr class="filaLic_1_1 filasLicitadores hidden">
        <td>%s</td><td>%s</td><td>%s</td></tr>
        """;
    return row.formatted(lote, nif, nameCell);
  }

  /** The shape 578 of the 613 measured rows have: one list, one item, no nesting. */
  private static String firmCell(String name) {
    return "<ul class='list-unstyled'><li>%s</li></ul>".formatted(name);
  }

  /**
   * The shape the other 35 have. The inner {@code <ul>} really is a direct child of the outer one,
   * with no {@code <li>} wrapping it — invalid HTML, and what the source serves.
   */
  private static String consortiumCell(String name, String... members) {
    String entries =
        Arrays.stream(members)
            .map("<li>%s</li>"::formatted)
            .collect(Collectors.joining());
    return "<ul class='list-unstyled'><li>%s</li><ul>%s</ul></ul>".formatted(name, entries);
  }

  /** As many indistinguishable single-firm rows as a count needs, all on one lote. */
  private static String firmRowsOfLote(String lote, int count) {
    return IntStream.range(0, count)
        .mapToObj(index -> bidderRow(lote, "B3674658%d".formatted(index), firmCell("ACME, S.L.")))
        .collect(Collectors.joining());
  }

  private static String awardTable(String rows) {
    return "<div class=\"tab-pane fade\" id=\"consulta-resolucion\">%s</div>"
        .formatted(table(AWARD_HEADER, rows));
  }

  /**
   * An award row that is unremarkable in every way but its lote and its participant count, so a
   * case about the cross-check shows only the two values it turns on.
   */
  private static String ordinaryAwardOfLote(String lote, String participants) {
    // The empty anchors and spans are the source's own: the count sits beside a glyphicon link.
    String row =
        """
        <tr><td>%s</td><td>%s<a href="#"><span class="glyphicon"></span></a></td>
        <td><span title="Adxudicado">Adxudicado</span></td><td>ACME, S.L.</td>
        <td class="text-right">1.000,00 €</td><td><a>01-02-2024</a></td>
        <td>3 meses</td><td></td></tr>
        """;
    return row.formatted(lote, participants);
  }
}
