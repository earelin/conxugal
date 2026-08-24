package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;

/**
 * One of the record's data tables, read by column header. The table analogue of
 * {@code LicitacionRecordDocument}'s labelled block, and the one place the rule lives:
 * <strong>columns are found by their header, never by their index.</strong>
 *
 * <p>Named for what it reads rather than for the shape it is, so it is not confused with
 * {@link LicitacionsTable}, which is the JSON listing's payload and lives in this package too.
 *
 * <p><strong>Absence and unreadability are different answers, and the difference is the whole
 * point of this type.</strong> A record publishing no resolution table is the ordinary case —
 * every <em>En curso</em>, <em>Pendente de adxudicar</em>, <em>Deserto</em>, <em>Anulado</em> and
 * <em>Renuncia</em> procedure has none, 278 of the ~2 000 listing rows sampled, and a contrato
 * menor served from the shared identifier space publishes neither classification table. Refusing
 * those would send every one of them to the outstanding ledger. But a table that <em>is</em>
 * published and can no longer be read means the parse has stopped understanding the source, and a
 * procedure stored from that is stored as authoritative with nothing ever returning to it.
 *
 * <p>So: <strong>absent</strong> is the container matching nothing, or matching and holding no
 * table — from a parse's point of view a panel published without its table says nothing, and
 * reading more into it would be an inference. <strong>Unreadable</strong> is a table that is there
 * but has no header row, or does not carry a column this parse reads, or has a body row too short
 * to hold one. Those raise.
 *
 * <p><strong>Only the columns a procedure would be worth losing over are required</strong>, which
 * is why they are given rather than taken from the header. Two different reasons apply. The
 * resolution table's {@code Recurso/ Prazo} is never read, so the source dropping it must not cost
 * a procedure. The lotes table's {@code Descrición} and {@code Valor estimado} <em>are</em> read
 * but are decoration — a lote exists whether or not they do — so they are looked up where present
 * and answer null where not. That distinction is load-bearing: this source is measured to drop a
 * header conditionally (the recursos modal publishes {@code Lote} on a procedure with lotes and
 * omits it on one without), and the lotes modal is the one container present on every record of
 * both families, so a required column there is checked on every procedure ever imported.
 *
 * <p><strong>A body row must be exactly as wide as the header.</strong> Cells are keyed by
 * position within their row, so a row that disagrees with the header cannot be keyed — one cell
 * too few or too many and every column past the discrepancy reads its neighbour's value. Measured,
 * every body row on every captured page matches its header exactly, and no cell anywhere carries
 * {@code colspan} or {@code rowspan}. A row that stops matching is the source rendering something
 * this parse has not seen, and the loud failure is the point: the alternative is a nationality
 * column holding an amount, stored as authoritative.
 *
 * <p>Every row is keyed on construction, so a refusal names the table it came from rather than
 * whichever column a caller happened to reach first, and a table that survives being built cannot
 * fail afterwards.
 */
final class PublishedTable {

  private final List<Row> rows;

  private PublishedTable(List<Row> rows) {
    this.rows = List.copyOf(rows);
  }

  /**
   * The table {@code containerSelector} holds, or nothing at all where the record publishes no
   * such section.
   *
   * <p>Every column the header carries is keyed, not merely the required ones, so a caller can ask
   * for decoration that may not be there. What is <em>required</em> is only what a procedure would
   * be worth losing over.
   *
   * @param requiredColumns the header labels without which the table cannot be read at all
   * @throws LicitacionRecordUnavailableException if the table is there and cannot be read
   */
  static Optional<PublishedTable> under(
      PublicationId publicationId,
      Document document,
      String containerSelector,
      String... requiredColumns) {
    Element container = document.selectFirst(containerSelector);
    if (container == null) {
      return Optional.empty();
    }
    Element table = container.selectFirst("table");
    if (table == null) {
      return Optional.empty();
    }
    Map<String, Integer> headers = headerIndex(table);
    for (String column : requiredColumns) {
      if (!headers.containsKey(column)) {
        throw new LicitacionRecordUnavailableException(
            "Record %s publishes a %s table with no %s column"
                .formatted(publicationId.value(), containerSelector, column));
      }
    }
    return Optional.of(
        new PublishedTable(read(publicationId, containerSelector, table, headers)));
  }

  /** The body rows, in the order the source published them. */
  List<Row> rows() {
    return rows;
  }

  private static List<Row> read(
      PublicationId publicationId,
      String containerSelector,
      Element table,
      Map<String, Integer> headers) {
    List<Row> rows = new ArrayList<>();
    for (Element row : table.select("> tbody > tr")) {
      List<Element> cells = row.select("> td");
      if (cells.size() != headers.size()) {
        throw new LicitacionRecordUnavailableException(
            "Record %s publishes a row of %d cells in its %s table, whose header carries %d"
                .formatted(
                    publicationId.value(), cells.size(), containerSelector, headers.size()));
      }
      Map<String, Element> keyed = new HashMap<>();
      headers.forEach((column, index) -> keyed.put(column, cells.get(index)));
      rows.add(new Row(keyed));
    }
    return rows;
  }

  /**
   * The header labels of {@code table}, keyed the same way {@code LicitacionRecordDocument} keys
   * its {@code <dt>} labels — reduced by {@link PublishedValues#label}, first occurrence winning.
   *
   * <p>Every header this source writes wraps its text in an anchor, so the label is taken from the
   * cell's whole text rather than from a child. A table with no header row answers empty, which
   * the caller reads as unreadable: it cannot be read by label, and reading it by index is what
   * this type exists to prevent.
   *
   * <p><strong>First occurrence winning is how the index is built, not what a repeat
   * costs.</strong> A repeated label indexes fewer columns than the header has cells, so a row
   * fails the width check and the table is refused. That is intended and not a side effect:
   * a repeated {@code <dt>} still names one value, whereas a repeated column heads two, and there
   * is no answering which of them a caller asking for that label meant.
   */
  private static Map<String, Integer> headerIndex(Element table) {
    Map<String, Integer> headers = new HashMap<>();
    List<Element> cells = table.select("> thead > tr > th");
    for (int index = 0; index < cells.size(); index++) {
      headers.putIfAbsent(PublishedValues.label(cells.get(index).wholeText()), index);
    }
    return headers;
  }

  /** One body row, whose cells are reached by the header they sat under. */
  static final class Row {

    private final Map<String, Element> cells;

    private Row(Map<String, Element> cells) {
      this.cells = Map.copyOf(cells);
    }

    /**
     * The cell under {@code column}, trimmed at its ends and reduced no further, or null where the
     * source published nothing there — <strong>or no such column at all</strong>, which is how a
     * decoration column that a required one would refuse over answers instead.
     *
     * <p>Read whole rather than as jsoup's tidied {@code text()}: this source breaks a value
     * across lines and joins a code to its description with a non-breaking space, and tidying
     * would silently return something it did not publish. The notation helpers this feeds collapse
     * internally, so one accessor serves both them and the two splits that need the untidied form.
     */
    @Nullable String text(String column) {
      Element cell = cells.get(column);
      return cell == null ? null : PublishedValues.text(cell.wholeText());
    }
  }
}
