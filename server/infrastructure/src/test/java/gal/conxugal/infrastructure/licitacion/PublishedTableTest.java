package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * The mechanism, on its own. {@link LicitacionRecordTablesTest} covers what each of the record's
 * five tables <em>means</em> and reaches this class through the whole parse; these cases are about
 * the reading itself, and several of them describe shapes no real table has.
 *
 * <p>Worth testing directly because this is where the judgement that costs a procedure lives.
 * A table read as absent when it was merely misunderstood loses data silently; one refused when it
 * was readable sends a perfectly good procedure to the outstanding ledger. Both are decided here,
 * on rules the callers never see.
 */
class PublishedTableTest {

  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");

  private static final String CONTAINER = "div#consulta-resolucion";

  private static final String LOTE = "Lote";
  private static final String IMPORTE = "Importe";

  // --- absent: an ordinary answer, never a refusal ---------------------------------------------

  @Test
  void answers_nothing_where_the_record_publishes_no_such_container() {
    Optional<PublishedTable> table = under("<div>Outra cousa</div>", LOTE);

    assertThat(table).isEmpty();
  }

  /**
   * A panel published without its table states nothing. Reading more into it would be an
   * inference, and the cost of guessing wrong is a real procedure in the outstanding ledger.
   */
  @Test
  void answers_nothing_where_the_container_holds_no_table() {
    Optional<PublishedTable> table = under(container("<p>Non hai datos</p>"), LOTE);

    assertThat(table).isEmpty();
  }

  /** Header row only. The table is readable; it simply has nothing in it. */
  @Test
  void answers_the_table_that_publishes_only_its_header() {
    Optional<PublishedTable> table = under(container(table(headers(LOTE), "")), LOTE);

    assertThat(table).isPresent();
    assertThat(table.orElseThrow().rows()).isEmpty();
  }

  // --- unreadable: the loud failure ------------------------------------------------------------

  /** The message names both the table and the column, because that is what a maintainer greps. */
  @Test
  void throws_where_the_table_carries_no_column_the_parse_requires() {
    assertThatThrownBy(() -> under(container(table(headers(LOTE), row("1"))), LOTE, IMPORTE))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining(IMPORTE)
        .hasMessageContaining(CONTAINER)
        .hasMessageContaining("822054");
  }

  /**
   * It cannot be read by label, and reading it by index is the one thing this type exists to
   * prevent — so a table with rows and no header is refused rather than guessed at.
   */
  @Test
  void throws_where_the_table_publishes_no_header_row() {
    String headerless = "<table><tbody><tr><td>1</td></tr></tbody></table>";

    assertThatThrownBy(() -> under(container(headerless), LOTE))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining(LOTE);
  }

  @Test
  void throws_where_the_row_is_narrower_than_the_header() {
    assertThatThrownBy(
            () -> under(container(table(headers(LOTE, IMPORTE), row("1"))), LOTE, IMPORTE))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("1 cells");
  }

  /**
   * The case that does not announce itself. A row one cell too wide parses perfectly well by
   * index and puts every value one column out — an amount read as a nationality, stored as
   * authoritative — so width is checked in both directions rather than only for shortness.
   */
  @Test
  void throws_where_the_row_is_wider_than_the_header() {
    assertThatThrownBy(
            () ->
                under(
                    container(table(headers(LOTE, IMPORTE), row("1", "1,00", "extra"))),
                    LOTE,
                    IMPORTE))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("3 cells");
  }

  /**
   * A header that says the same thing twice cannot be keyed: the second occurrence is dropped, so
   * the header indexes fewer columns than the row has cells and the width check refuses the table.
   *
   * <p>Pinned because the outcome is not obvious from either rule on its own — the first-wins
   * keying reads as though a repeat were tolerated — and because refusing is the answer that fails
   * loudly. Under the labelled scalars the same shape is tolerated, and the difference is real: a
   * repeated {@code <dt>} still names one value, whereas a repeated column heads two.
   */
  @Test
  void throws_where_the_header_says_the_same_thing_twice() {
    String repeated = headers(LOTE, LOTE, IMPORTE);

    assertThatThrownBy(
            () -> under(container(table(repeated, row("1", "2", "1,00"))), LOTE, IMPORTE))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("3 cells");
  }

  // --- reading by header, never by index -------------------------------------------------------

  /**
   * The rule the whole type exists for, stated at its own level: the same two columns in the
   * opposite order read the same two values.
   */
  @Test
  void reads_the_column_by_its_header_wherever_the_header_sits() {
    PublishedTable.Row straight =
        onlyRow(table(headers(LOTE, IMPORTE), row("1", "1,00")), LOTE, IMPORTE);
    PublishedTable.Row reversed =
        onlyRow(table(headers(IMPORTE, LOTE), row("1,00", "1")), LOTE, IMPORTE);

    assertThat(straight.text(LOTE)).isEqualTo("1");
    assertThat(straight.text(IMPORTE)).isEqualTo("1,00");
    assertThat(reversed.text(LOTE)).isEqualTo("1");
    assertThat(reversed.text(IMPORTE)).isEqualTo("1,00");
  }

  /** Every header this source writes wraps its text in an anchor, so the label is the cell's. */
  @Test
  void reads_the_header_label_past_the_link_the_source_wraps_it_in() {
    String linked = "<th scope=\"col\" class=\"th_5\"><a href=\"#\">Lote</a></th>";
    PublishedTable.Row read = onlyRow(table(linked, row("1")), LOTE);

    assertThat(read.text(LOTE)).isEqualTo("1");
  }

  /**
   * The header is keyed whole, so a column the caller did not require is still reachable. That is
   * what lets a decoration column be looked up where present without costing a procedure where it
   * is not.
   */
  @Test
  void keys_every_column_the_header_carries_not_only_the_required_ones() {
    PublishedTable.Row read = onlyRow(table(headers(LOTE, IMPORTE), row("1", "1,00")), LOTE);

    assertThat(read.text(IMPORTE)).isEqualTo("1,00");
  }

  /** The other half of the same rule: an absent decoration column answers, it does not raise. */
  @Test
  void answers_nothing_for_the_column_the_header_does_not_carry() {
    PublishedTable.Row read = onlyRow(table(headers(LOTE), row("1")), LOTE);

    assertThat(read.text("Descrición")).isNull();
  }

  // --- what a cell says ------------------------------------------------------------------------

  /**
   * Read whole rather than through jsoup's tidied {@code text()}, which collapses internal runs.
   * A published value really does contain a double space, and a parse that tidied it would store
   * something the source never published while every hand-written fixture went on passing.
   */
  @Test
  void trims_the_cell_at_its_ends_and_nowhere_else() {
    String published = "<tr><td>\n   dentro do  programa\n   de impulso   \n</td></tr>";
    PublishedTable.Row read = onlyRow(table(headers(LOTE), published), LOTE);

    assertThat(read.text(LOTE)).isEqualTo("dentro do  programa\n   de impulso");
  }

  @Test
  void answers_nothing_for_the_cell_the_source_left_empty() {
    PublishedTable.Row read = onlyRow(table(headers(LOTE), row("   ")), LOTE);

    assertThat(read.text(LOTE)).isNull();
  }

  // --- scope ------------------------------------------------------------------------------------

  @Test
  void answers_the_rows_in_the_order_the_source_published_them() {
    String rows = row("1") + row("2") + row("3");
    Optional<PublishedTable> table = under(container(table(headers(LOTE), rows)), LOTE);

    assertThat(table.orElseThrow().rows())
        .extracting(row -> row.text(LOTE))
        .containsExactly("1", "2", "3");
  }

  /**
   * The record nests a whole table inside a cell of another. Cells and rows are selected as direct
   * children for that reason: a document-wide walk would read the inner table's rows as the outer
   * table's, and they are not even the same shape.
   */
  @Test
  void ignores_the_rows_of_the_table_nested_inside_this_one() {
    String nested =
        """
        <tr><td>1<table><tbody><tr><td>agochado</td></tr></tbody></table></td></tr>
        """;
    Optional<PublishedTable> table = under(container(table(headers(LOTE), nested)), LOTE);

    assertThat(table.orElseThrow().rows()).hasSize(1);
  }

  /** Nothing a caller does to the answer can change what the record was read as saying. */
  @Test
  void answers_rows_that_cannot_be_changed_afterwards() {
    Optional<PublishedTable> table = under(container(table(headers(LOTE), row("1"))), LOTE);

    assertThat(table.orElseThrow().rows()).isUnmodifiable();
  }

  private static Optional<PublishedTable> under(String body, String... requiredColumns) {
    Document document = Jsoup.parse(body, "/licitacion");
    return PublishedTable.under(PUBLICATION_ID, document, CONTAINER, requiredColumns);
  }

  /** The single row of a table this case expects to be readable. */
  private static PublishedTable.Row onlyRow(String markup, String... requiredColumns) {
    Optional<PublishedTable> table = under(container(markup), requiredColumns);
    assertThat(table).isPresent();
    assertThat(table.orElseThrow().rows()).hasSize(1);
    return table.orElseThrow().rows().getFirst();
  }

  private static String container(String body) {
    return "<div class=\"tab-pane fade\" id=\"consulta-resolucion\">%s</div>".formatted(body);
  }

  private static String table(String header, String rows) {
    return "<table class=\"table\"><thead><tr>%s</tr></thead><tbody>%s</tbody></table>"
        .formatted(header, rows);
  }

  private static String headers(String... labels) {
    StringBuilder header = new StringBuilder();
    for (String label : labels) {
      header.append("<th scope=\"col\">").append(label).append("</th>");
    }
    return header.toString();
  }

  private static String row(String... cells) {
    StringBuilder row = new StringBuilder("<tr>");
    for (String cell : cells) {
      row.append("<td>").append(cell).append("</td>");
    }
    return row.append("</tr>").toString();
  }
}
