package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.licitacion.LoteKey;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAmount;
import gal.conxugal.domain.licitacion.PublishedAward;
import gal.conxugal.domain.licitacion.PublishedCpvClassification;
import gal.conxugal.domain.licitacion.PublishedFormalisation;
import gal.conxugal.domain.licitacion.PublishedLote;
import gal.conxugal.domain.licitacion.PublishedNutClassification;
import gal.conxugal.domain.money.Money;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.Nullable;

/**
 * What the record's five data tables say. Its sibling {@code LicitacionRecordDocument} owns
 * whether the page is a record at all and what its labelled scalars state; this owns the tables,
 * and the two are apart because they turn on two different rules with two different failure
 * modes — a scalar read by position answers a plausible value from the wrong field, whereas a
 * table read by position loses or misfiles a whole procedure's awards.
 *
 * <p>The sixth table, the bidder list, is not here: its consortium branch is a design in its own
 * right.
 *
 * <p><strong>Every table is read on construction</strong>, so a record the source has changed
 * shape on is refused once, before any value is handed out, rather than part-way through a caller
 * that has already taken some.
 *
 * <p><strong>Every lote cell goes through {@link LoteKey}.</strong> The award, formalisation and
 * NUT tables write {@code _} for a procedure-wide row, zero-padding varies within a single table
 * (the award table produced both {@code 1} and {@code 05} in one sample), and a lote identifier is
 * not always numeric. Joined on the raw cell the award table's participant count agrees with the
 * bidder rows on 63 of 158 rows; normalised, on all 158.
 */
final class LicitacionRecordTables {

  private static final String RESOLUTION_TABLE = "div#consulta-resolucion";
  private static final String FORMALISATION_TABLE = "div#consulta-formalizacion";
  private static final String CPV_TABLE = "div#collapseCPV";
  private static final String NUT_TABLE = "div#collapseNUT";
  private static final String LOTES_TABLE = "div#ventanaModalLotes";

  private static final String LOTE = "Lote";
  private static final String IMPORTE = "Importe";
  private static final String DATA_DIFUSION = "Data difusión";

  private static final String PARTICIPANTS = "Part.";
  private static final String RESOLUCION = "Resolución";
  private static final String ADXUDICATARIO = "Adxudicatario";
  private static final String PRAZO = "Prazo de execución";

  private static final String DATA_FORMALIZACION = "Data formalización";
  private static final String CONTRATISTA = "Contratista";
  private static final String NACIONALIDADE = "Nacionalidade";

  /** Lower-case {@code c}, as the source writes it. */
  private static final String CODIGO_CPV = "código CPV";

  private static final String NUT_CODE = "NUT";
  private static final String DESCRICION = "Descrición";
  private static final String VALOR_ESTIMADO = "Valor estimado";

  private final List<PublishedAward> awards = new ArrayList<>();
  private final List<PublishedFormalisation> formalisations = new ArrayList<>();
  private final List<PublishedCpvClassification> cpvClassifications = new ArrayList<>();
  private final List<PublishedNutClassification> nutClassifications = new ArrayList<>();
  private final List<PublishedLote> lotes = new ArrayList<>();

  /**
   * Reads every table {@code document} publishes.
   *
   * @throws gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException if a table is
   *     present and cannot be read
   */
  LicitacionRecordTables(PublicationId publicationId, Document document) {
    // The published spelling of every lote any table names, in the order the tables are read, so
    // that the lote is stored under the spelling the award table used rather than a later one's.
    List<String> loteCells = new ArrayList<>();

    readAwards(publicationId, document, loteCells);
    readFormalisations(publicationId, document, loteCells);
    readCpvClassifications(publicationId, document, loteCells);
    readNutClassifications(publicationId, document, loteCells);
    readLotes(publicationId, document, loteCells);
  }

  List<PublishedAward> awards() {
    return List.copyOf(awards);
  }

  List<PublishedFormalisation> formalisations() {
    return List.copyOf(formalisations);
  }

  List<PublishedCpvClassification> cpvClassifications() {
    return List.copyOf(cpvClassifications);
  }

  List<PublishedNutClassification> nutClassifications() {
    return List.copyOf(nutClassifications);
  }

  List<PublishedLote> lotes() {
    return List.copyOf(lotes);
  }

  private void readAwards(
      PublicationId publicationId, Document document, List<String> loteCells) {
    Optional<PublishedTable> table =
        PublishedTable.under(
            publicationId,
            document,
            RESOLUTION_TABLE,
            LOTE,
            PARTICIPANTS,
            RESOLUCION,
            ADXUDICATARIO,
            IMPORTE,
            DATA_DIFUSION,
            PRAZO);
    if (table.isEmpty()) {
      return;
    }
    for (PublishedTable.Row row : table.get().rows()) {
      String lote = row.text(LOTE);
      loteCells.add(lote);
      awards.add(
          new PublishedAward(
              lote,
              row.text(RESOLUCION),
              PublishedValues.date(row.text(DATA_DIFUSION)),
              amountOf(row.text(IMPORTE)),
              row.text(PRAZO),
              row.text(ADXUDICATARIO),
              PublishedValues.count(row.text(PARTICIPANTS))));
    }
  }

  private void readFormalisations(
      PublicationId publicationId, Document document, List<String> loteCells) {
    Optional<PublishedTable> table =
        PublishedTable.under(
            publicationId,
            document,
            FORMALISATION_TABLE,
            DATA_FORMALIZACION,
            LOTE,
            CONTRATISTA,
            NACIONALIDADE,
            IMPORTE);
    if (table.isEmpty()) {
      return;
    }
    for (PublishedTable.Row row : table.get().rows()) {
      String lote = row.text(LOTE);
      loteCells.add(lote);
      ContratistaCell contratista = ContratistaCell.read(row.text(CONTRATISTA));
      formalisations.add(
          new PublishedFormalisation(
              lote,
              PublishedValues.date(row.text(DATA_FORMALIZACION)),
              contratista.name(),
              contratista.fiscalIdentifier(),
              row.text(NACIONALIDADE),
              amountOf(row.text(IMPORTE))));
    }
  }

  private void readCpvClassifications(
      PublicationId publicationId, Document document, List<String> loteCells) {
    Optional<PublishedTable> table =
        PublishedTable.under(
            publicationId, document, CPV_TABLE, CODIGO_CPV, LOTE, DATA_DIFUSION);
    if (table.isEmpty()) {
      return;
    }
    for (PublishedTable.Row row : table.get().rows()) {
      String cell = row.text(CODIGO_CPV);
      String code = PublishedValues.code(cell);
      if (code == null) {
        continue;
      }
      String lote = row.text(LOTE);
      loteCells.add(lote);
      cpvClassifications.add(
          new PublishedCpvClassification(
              code,
              PublishedValues.description(cell),
              lote,
              PublishedValues.date(row.text(DATA_DIFUSION))));
    }
  }

  private void readNutClassifications(
      PublicationId publicationId, Document document, List<String> loteCells) {
    Optional<PublishedTable> table =
        PublishedTable.under(
            publicationId, document, NUT_TABLE, NUT_CODE, LOTE, DATA_DIFUSION);
    if (table.isEmpty()) {
      return;
    }
    for (PublishedTable.Row row : table.get().rows()) {
      String cell = row.text(NUT_CODE);
      String code = PublishedValues.code(cell);
      if (code == null) {
        continue;
      }
      String lote = row.text(LOTE);
      loteCells.add(lote);
      nutClassifications.add(
          new PublishedNutClassification(
              code,
              PublishedValues.description(cell),
              lote,
              PublishedValues.date(row.text(DATA_DIFUSION))));
    }
  }

  /**
   * The procedure's lotes: every lote any table names, decorated with what
   * {@code Relación de lotes} publishes about it.
   *
   * <p><strong>The lotes table alone would not do.</strong> On 822054 it is header-row-only while
   * {@code Nº lotes} says {@code 2} and the award table names both, so a parse that discovered
   * lotes there would have found none and lost both awards with them. The other four tables are
   * read for the same reason in the other direction: a formalisation or a classification naming a
   * lote no award row mentions would otherwise have no lote to hang on.
   *
   * <p>The first spelling met wins, and the tables are read award-first, so a lote written
   * {@code 05} by the award table and {@code 5} by another is stored as {@code 05}. That is the
   * same rule the labelled scalars use for a repeated label, and it keeps the stored identifier
   * the one the awarding table published.
   */
  private void readLotes(
      PublicationId publicationId, Document document, List<String> loteCells) {
    Map<String, PublishedLote> decoration = new LinkedHashMap<>();
    List<String> ownCells = new ArrayList<>();
    Optional<PublishedTable> table =
        PublishedTable.under(
            publicationId, document, LOTES_TABLE, LOTE, DESCRICION, VALOR_ESTIMADO);
    if (table.isPresent()) {
      for (PublishedTable.Row row : table.get().rows()) {
        String cell = row.text(LOTE);
        Optional<String> key = LoteKey.normalise(cell);
        if (key.isEmpty()) {
          continue;
        }
        ownCells.add(cell);
        decoration.putIfAbsent(
            key.get(),
            new PublishedLote(
                cell, row.text(DESCRICION), amountOf(row.text(VALOR_ESTIMADO))));
      }
    }

    Map<String, PublishedLote> named = new LinkedHashMap<>();
    List<String> everyCell = new ArrayList<>(loteCells);
    everyCell.addAll(ownCells);
    for (String cell : everyCell) {
      Optional<String> key = LoteKey.normalise(cell);
      if (key.isEmpty()) {
        continue;
      }
      PublishedLote published = decoration.get(key.get());
      named.putIfAbsent(
          key.get(),
          published == null
              ? new PublishedLote(cell, null, null)
              : new PublishedLote(cell, published.description(), published.estimatedValue()));
    }
    lotes.addAll(named.values());
  }

  /**
   * The figure a cell states, with the tax basis dropped. Every award point holds a bare
   * {@link Money}: no measured row of either table labels its {@code Importe} {@code con IVE} or
   * {@code sen IVE}, unlike the procedure's own two budgets, so there is no basis to carry and a
   * component for one would be null on every row the source publishes.
   */
  private static @Nullable Money amountOf(@Nullable String published) {
    PublishedAmount amount = PublishedValues.amount(published);
    return amount == null ? null : amount.amount();
  }
}
