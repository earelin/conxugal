package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.LoteKey;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAmount;
import gal.conxugal.domain.licitacion.PublishedAward;
import gal.conxugal.domain.licitacion.PublishedBidder;
import gal.conxugal.domain.licitacion.PublishedCpvClassification;
import gal.conxugal.domain.licitacion.PublishedFormalisation;
import gal.conxugal.domain.licitacion.PublishedLote;
import gal.conxugal.domain.licitacion.PublishedNutClassification;
import gal.conxugal.domain.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.Nullable;

/**
 * What the record's six data tables say. Its sibling {@code LicitacionRecordDocument} owns whether
 * the page is a record at all and what its labelled scalars state; this owns the tables, and the
 * two are apart because they turn on two different rules with two different failure modes — a
 * scalar read by position answers a plausible value from the wrong field, whereas a table read by
 * position loses or misfiles a whole procedure's awards.
 *
 * <p><strong>Every table is read on construction</strong>, so a record the source has changed
 * shape on is refused once, before any value is handed out, rather than part-way through a caller
 * that has already taken some.
 *
 * <p><strong>Every lote cell goes through {@link LoteKey}.</strong> The award, formalisation and
 * NUT tables write {@code _} for a procedure-wide row while the bidder table writes {@code -},
 * zero-padding varies within a single table (the award table produced both {@code 1} and {@code 05}
 * in one sample), and a lote identifier is not always numeric. Joined on the raw cell the award
 * table's participant count agrees with the bidder rows on 63 of 158 rows; normalised, on all 158.
 *
 * <p><strong>The bidder table and the award table check each other</strong>, which is the one place
 * two of these tables are read against one another rather than side by side. What the check is and
 * where it stops is {@link #crossCheckParticipantCounts}'s.
 */
final class LicitacionRecordTables {

  private static final String RESOLUTION_TABLE = "div#consulta-resolucion";
  private static final String FORMALISATION_TABLE = "div#consulta-formalizacion";
  private static final String CPV_TABLE = "div#collapseCPV";
  private static final String NUT_TABLE = "div#collapseNUT";
  private static final String LOTES_TABLE = "div#ventanaModalLotes";
  private static final String BIDDERS_TABLE = "div#ventanaModalLicitadores";

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

  private static final String NIF = "NIF";
  private static final String NOME = "Nome";

  private final List<PublishedAward> awards;
  private final List<PublishedFormalisation> formalisations;
  private final List<PublishedCpvClassification> cpvClassifications;
  private final List<PublishedNutClassification> nutClassifications;
  private final List<PublishedBidder> bidders;
  private final List<PublishedLote> lotes;

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

    // Copied here rather than in each accessor, so the one place a field is set is the one place
    // that says what it holds: a list nothing can change afterwards.
    this.awards = List.copyOf(readAwards(publicationId, document, loteCells));
    this.formalisations = List.copyOf(readFormalisations(publicationId, document, loteCells));
    this.cpvClassifications =
        List.copyOf(
            readClassifications(
                publicationId,
                document,
                loteCells,
                CPV_TABLE,
                CODIGO_CPV,
                PublishedCpvClassification::new));
    this.nutClassifications =
        List.copyOf(
            readClassifications(
                publicationId,
                document,
                loteCells,
                NUT_TABLE,
                NUT_CODE,
                PublishedNutClassification::new));
    // Read after the four tables above so a lote they name keeps their spelling, and before the
    // lotes are assembled so a lote only the bidder rows name is still one: a procedure that
    // publishes bidders before it publishes any award has no other table to be discovered from.
    this.bidders = List.copyOf(readBidders(publicationId, document, loteCells));
    this.lotes = List.copyOf(readLotes(publicationId, document, loteCells));
    crossCheckParticipantCounts(publicationId, this.awards, this.bidders);
  }

  List<PublishedAward> awards() {
    return awards;
  }

  List<PublishedFormalisation> formalisations() {
    return formalisations;
  }

  List<PublishedCpvClassification> cpvClassifications() {
    return cpvClassifications;
  }

  List<PublishedNutClassification> nutClassifications() {
    return nutClassifications;
  }

  List<PublishedBidder> bidders() {
    return bidders;
  }

  List<PublishedLote> lotes() {
    return lotes;
  }

  private List<PublishedAward> readAwards(
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
      return List.of();
    }
    List<PublishedAward> awards = new ArrayList<>();
    for (PublishedTable.Row row : table.get().rows()) {
      String lote = row.text(LOTE);
      addLoteCell(loteCells, lote);
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
    return awards;
  }

  private List<PublishedFormalisation> readFormalisations(
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
      return List.of();
    }
    List<PublishedFormalisation> formalisations = new ArrayList<>();
    for (PublishedTable.Row row : table.get().rows()) {
      String lote = row.text(LOTE);
      addLoteCell(loteCells, lote);
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
    return formalisations;
  }

  /**
   * The bidders {@code Relación de licitadores presentados} names, one per row and each classified
   * by its own markup — which is {@link LicitadorRow}'s, and the decision this feature most depends
   * on getting right.
   *
   * <p><strong>The name cell is reached as markup rather than as text</strong>, because its
   * structure is the fact: a consortium nests a second list inside the first and a single firm does
   * not. Every other cell on the record is read as text, and this is the one exception.
   *
   * <p>Only {@code Lote}, {@code NIF} and {@code Nome} are required. The header is not fixed —
   * 828959 publishes a fourth {@code NUT} column that 822054 does not — so a positional read would
   * misfile one of those two procedures, and requiring a column the source drops conditionally
   * would lose it outright.
   */
  private List<PublishedBidder> readBidders(
      PublicationId publicationId, Document document, List<String> loteCells) {
    Optional<PublishedTable> table =
        PublishedTable.under(publicationId, document, BIDDERS_TABLE, LOTE, NIF, NOME);
    if (table.isEmpty()) {
      return List.of();
    }
    List<PublishedBidder> bidders = new ArrayList<>();
    for (PublishedTable.Row row : table.get().rows()) {
      String lote = row.text(LOTE);
      addLoteCell(loteCells, lote);
      bidders.add(LicitadorRow.read(lote, row.text(NIF), row.element(NOME)));
    }
    return bidders;
  }

  /**
   * The award table's {@code Part.} against the bidder rows it stands for. A parse producing a
   * different count <strong>has failed</strong>, and the procedure is refused rather than stored
   * with a short list: a silently short bidder list is indistinguishable from a genuine one and
   * would understate competition for ever.
   *
   * <p><strong>Joined on {@link LoteKey}'s reduction, never on the raw cell.</strong> Measured over
   * 240 procedures, the raw join disagrees on 95 of 158 award rows — every failure an artefact of
   * {@code _} against {@code -} or {@code 05} against {@code 5} — and agrees on all 158 normalised.
   * Unnormalised, this check would refuse most procedures the source publishes perfectly well.
   *
   * <p><strong>It applies only to a lote whose bidder table was published, and that precedence is
   * stated because the two rules otherwise disagree in silence.</strong> An absent bidder table is
   * an empty list whatever {@code Part.} says: 26 of the first 70 procedures sampled published no
   * bidder table at all — open, pending, deserted or withdrawn — and nothing measured rules out an
   * award row carrying a non-zero count on such a page. The source's own modal is per-lote, so a
   * lote the table names no row for has not published its bidders either.
   *
   * <p>The cost, which is real and has two forms. The stated one: a lote whose {@code Part.} says 5
   * and which publishes no row of its own passes unchecked. The one worth knowing about, because
   * this check cannot tell it from the first: <strong>a lote key that stops matching is
   * indistinguishable from a lote whose bidders were never published.</strong> Were the source to
   * begin writing the bidder table's lote cell as {@code Lote 1} rather than {@code 1}, no bidder
   * key would meet any award key, every row would be skipped, and the guard would report nothing
   * on a record it no longer understands.
   *
   * <p>Refusing when the table holds rows and none of their keys meets any award key would catch
   * that — and would also refuse a multi-lote procedure whose awards and whose published bidders do
   * not yet overlap, which nothing measured rules out. It is left undone for want of the sample
   * that would settle which of the two is real. What is bought meanwhile is that no procedure is
   * refused over a table the source simply did not publish for it.
   */
  private static void crossCheckParticipantCounts(
      PublicationId publicationId,
      Iterable<PublishedAward> awards,
      Iterable<PublishedBidder> bidders) {
    Map<String, Integer> published = biddersPerLote(bidders);
    for (PublishedAward award : awards) {
      Integer stated = award.bidderCount();
      Integer parsed = published.get(award.loteKey());
      if (stated == null || parsed == null || stated.equals(parsed)) {
        continue;
      }
      throw new LicitacionRecordUnavailableException(
          "Record %s states %d participants for %s and publishes %d bidder rows"
              .formatted(publicationId.value(), stated, awardPoint(award.loteKey()), parsed));
    }
  }

  /**
   * How many rows the bidder table published for each award point it named.
   *
   * <p>Keyed on the same reduction the awards are, so the procedure-wide bucket the bidder table
   * writes {@code -} for is the one the award table writes {@code _} for. That key is null, which
   * is why this is a map that admits one rather than a lookup that cannot.
   */
  private static Map<String, Integer> biddersPerLote(Iterable<PublishedBidder> bidders) {
    Map<String, Integer> perLote = new HashMap<>();
    for (PublishedBidder bidder : bidders) {
      perLote.merge(bidder.loteKey(), 1, Integer::sum);
    }
    return perLote;
  }

  /** How a refusal names the award point, so a lotless procedure reads as a sentence. */
  private static String awardPoint(@Nullable String loteKey) {
    return loteKey == null ? "the procedure as a whole" : "lote %s".formatted(loteKey);
  }

  /**
   * How a classification row becomes its own vocabulary's kind of row. The two vocabularies are
   * separate types on purpose — a row filed under the wrong one classifies a procedure by a region
   * as though it were a purpose — so the parse they share is written once and the type it yields
   * is the caller's.
   */
  private interface Classification<T> {
    T of(
        String code,
        @Nullable String description,
        @Nullable String loteKey,
        @Nullable LocalDate diffusionDate);
  }

  /**
   * One vocabulary's table, read the one way both are read: the code cell carries the code and the
   * list's own wording behind a non-breaking space, the lote is named whether or not the row
   * yields a classification, and a row with no code yields none.
   *
   * <p>Written once because the two tables differ only in where they sit and what their code
   * column is called. Two copies of this drifted apart the first time a rule changed.
   */
  private <T> List<T> readClassifications(
      PublicationId publicationId,
      Document document,
      List<String> loteCells,
      String container,
      String codeColumn,
      Classification<T> classification) {
    Optional<PublishedTable> table =
        PublishedTable.under(publicationId, document, container, codeColumn, LOTE, DATA_DIFUSION);
    if (table.isEmpty()) {
      return List.of();
    }
    List<T> classifications = new ArrayList<>();
    for (PublishedTable.Row row : table.get().rows()) {
      String lote = row.text(LOTE);
      addLoteCell(loteCells, lote);
      String cell = row.text(codeColumn);
      String code = PublishedValues.code(cell);
      if (code == null) {
        continue;
      }
      classifications.add(
          classification.of(
              code,
              PublishedValues.description(cell),
              lote,
              PublishedValues.date(row.text(DATA_DIFUSION))));
    }
    return classifications;
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
  private List<PublishedLote> readLotes(
      PublicationId publicationId, Document document, Iterable<String> loteCells) {
    Map<String, PublishedLote> decoration = readLoteDecoration(publicationId, document);

    Map<String, PublishedLote> named = new LinkedHashMap<>();
    for (String cell : loteCells) {
      Optional<String> key = LoteKey.normalise(cell);
      if (key.isEmpty() || named.containsKey(key.get())) {
        continue;
      }
      named.put(key.get(), decorated(cell, decoration.get(key.get())));
    }
    // The lotes table's own rows last: a lote only it names is still a lote, and one an earlier
    // table already named keeps that table's spelling.
    decoration.forEach(named::putIfAbsent);
    return new ArrayList<>(named.values());
  }

  /**
   * What {@code Relación de lotes} says about each lote it names, keyed for the join.
   *
   * <p>Only the lote column is required. The other two are decoration a lote exists without, and
   * this is the one table the source publishes on every record of both families — so refusing over
   * a column it may drop would cost every procedure in the catalogue rather than one.
   */
  private Map<String, PublishedLote> readLoteDecoration(
      PublicationId publicationId, Document document) {
    Map<String, PublishedLote> decoration = new LinkedHashMap<>();
    Optional<PublishedTable> table =
        PublishedTable.under(publicationId, document, LOTES_TABLE, LOTE);
    if (table.isEmpty()) {
      return decoration;
    }
    for (PublishedTable.Row row : table.get().rows()) {
      String cell = row.text(LOTE);
      Optional<String> key = LoteKey.normalise(cell);
      if (key.isEmpty() || decoration.containsKey(key.get())) {
        continue;
      }
      decoration.put(
          key.get(),
          new PublishedLote(cell, row.text(DESCRICION), amountOf(row.text(VALOR_ESTIMADO))));
    }
    return decoration;
  }

  /** The lote {@code cell} names, carrying whatever the lotes table published about it. */
  private static PublishedLote decorated(String cell, @Nullable PublishedLote decoration) {
    return decoration == null
        ? new PublishedLote(cell, null, null)
        : new PublishedLote(cell, decoration.description(), decoration.estimatedValue());
  }

  /**
   * The published lote spelling this row names, where it names one. A row whose cell is absent
   * names no lote and contributes none — and never a null, so the list stays one every caller can
   * walk without a branch.
   *
   * <p>Called before a row is judged for anything else, so a lote is still named by a row that
   * yields nothing: a classification whose code cell is empty makes no classification, but the
   * lote it hangs on is real and something else on the record may hang on it too.
   */
  private static void addLoteCell(List<String> loteCells, @Nullable String cell) {
    if (cell != null) {
      loteCells.add(cell);
    }
  }

  /**
   * The figure a cell states, with any tax basis it states dropped.
   *
   * <p><strong>Dropped because the award point has nowhere to put it</strong>, not because the
   * source never states one. {@link Money} is what {@code Award}, {@code Formalisation} and
   * {@code Lote} each hold, and none carries a basis beside it — unlike the procedure's own two
   * budgets, which do.
   *
   * <p>For the two {@code Importe} columns that costs nothing measurable: no measured row of
   * either table labels its figure {@code con IVE} or {@code sen IVE}. <strong>For the lotes
   * table's {@code Valor estimado} it is a real gap</strong>, and an unmeasured one — every
   * captured record's lotes table is header-row-only, so no fixture exercises the column at all,
   * and the procedure-level {@code Valor estimado} on the same page <em>is</em> measured to vary
   * its basis between procedures. A lote value published {@code sen IVE} is therefore stored
   * beside a procedure value that kept its basis, with nothing recording that the two may differ.
   * Closing it means a basis on the stored lote, which is a change to the award-point model rather
   * than to this parse.
   */
  private static @Nullable Money amountOf(@Nullable String published) {
    PublishedAmount amount = PublishedValues.amount(published);
    return amount == null ? null : amount.amount();
  }
}
