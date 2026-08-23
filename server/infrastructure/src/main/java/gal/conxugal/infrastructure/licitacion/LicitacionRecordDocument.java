package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;

/**
 * What a fetched page means. The adapter owns the exchange and the judgement of whether an answer
 * arrived at all; this owns whether the answer is a record, and what it says.
 *
 * <p><strong>Values are found by label, never by position.</strong> The page's own blocks vary — a
 * procedure without lotes publishes six labelled pairs where one with lotes publishes ten — so the
 * nth pair is a different field on different procedures, and a parse that counted would read
 * plausible values out of the wrong fields.
 */
final class LicitacionRecordDocument {

  /**
   * The block the procedure's own fields are published in.
   *
   * <p><strong>Scoping to it is not tidiness.</strong> Three of the labels read here occur more
   * than once in the page: {@code Obxecto} appears again, empty, in the acordo-marco and sistema
   * dinámico blocks, and {@code Orzamento base de licitación} and {@code Valor estimado} appear
   * again in a restructuring panel. A document-wide lookup finds whichever copy the selector
   * reached first, and would answer an empty object for any procedure whose blocks were ordered
   * otherwise. Measured across the captured records this block holds each label exactly once, and
   * holds none of the copies.
   */
  private static final String MAIN_BLOCK = "div.infoConcurso";

  /** The procedure's reference, published in a block of its own and on a minority of records. */
  private static final String REFERENCE_BLOCK = "div.infoReferencia";

  private static final String REFERENCIA = "Referencia";

  /**
   * The state is not a labelled pair. It is published above the block as running text with the
   * value emphasised, which is why it is reached by its own selector rather than by label.
   */
  private static final String STATE_LABEL = "Estado do procedemento";

  private static final String OBXECTO = "Obxecto";

  private final PublicationId publicationId;
  private final Document document;
  private final Map<String, Element> labelled;

  private LicitacionRecordDocument(
      PublicationId publicationId, Document document, Map<String, Element> labelled) {
    this.publicationId = publicationId;
    this.document = document;
    this.labelled = labelled;
  }

  /**
   * The record {@code document} publishes for {@code publicationId}.
   *
   * @throws LicitacionRecordUnavailableException if the page is not a procedure's record
   */
  static LicitacionRecord read(PublicationId publicationId, Document document) {
    Element block = document.selectFirst(MAIN_BLOCK);
    if (block == null) {
      // An unknown identifier answers 200 with the site's error page rather than 404, so this is
      // what a procedure that is not there looks like — as is a page replaced by a notice. Neither
      // is a record, and no status can tell either of them from one.
      throw new LicitacionRecordUnavailableException(
          "Response for %s is not a procedure record: no %s block"
              .formatted(publicationId.value(), MAIN_BLOCK));
    }
    return new LicitacionRecordDocument(publicationId, document, labelledFields(block)).toRecord();
  }

  /**
   * Each label mapped to the value published against it. The first occurrence wins, so a block that
   * somehow repeated a label reads as the source's first statement of it rather than as its last.
   *
   * <p>The key is reduced with {@link Whitespace}, not {@link String#strip()}, which leaves a
   * non-breaking space in place — the page carries dozens of them, and a label ending in one would
   * key an entry no lookup could reach, reading the field as absent with nothing to say why.
   */
  private static Map<String, Element> labelledFields(Element block) {
    Map<String, Element> fields = new HashMap<>();
    for (Element term : block.select("dt")) {
      Element value = term.nextElementSibling();
      if (value != null && "dd".equals(value.tagName())) {
        fields.putIfAbsent(Whitespace.strip(term.wholeText()), value);
      }
    }
    return fields;
  }

  private LicitacionRecord toRecord() {
    String stateLabel = stateLabel();
    requireLabel(OBXECTO);
    return new LicitacionRecord(
        publicationId,
        expediente(),
        valueOf(OBXECTO),
        valueOf("Tipo de contrato"),
        valueOf("Tipo de procedemento"),
        valueOf("Tipo de tramitación"),
        PublishedValues.count(valueOf("Nº lotes")),
        PublishedValues.amount(valueOf("Orzamento base de licitación")),
        PublishedValues.amount(valueOf("Valor estimado")),
        stateLabel);
  }

  /**
   * The value published against {@code label}, or null where the record carries no such label.
   *
   * <p>Absence is an ordinary answer for all but the object. The page is served from an identifier
   * space shared with contratos menores, so {@code Nº lotes} and {@code Valor estimado} are
   * genuinely missing from real pages, and the reference is missing from most.
   */
  private @Nullable String valueOf(String label) {
    return PublishedValues.text(textOf(labelled.get(label)));
  }

  /**
   * The procedure's reference, which the record labels {@code Referencia} and publishes in a block
   * of its own rather than among the fields above.
   *
   * <p>Read by its label like everything else, and not as "the block's first {@code <dd>}". The
   * block carries one pair today, so position would work today — and would answer a neighbouring
   * value, silently and plausibly, the day the source adds a pair before it.
   */
  private @Nullable String expediente() {
    Element block = document.selectFirst(REFERENCE_BLOCK);
    return block == null
        ? null
        : PublishedValues.text(textOf(labelledFields(block).get(REFERENCIA)));
  }

  /**
   * The object's label and the state are what the record is refused over: a page carrying neither
   * is not a procedure's record, and one stored from a partial parse is stored as authoritative
   * with nothing ever returning to it.
   *
   * <p><strong>The label, not the value.</strong> A record publishing the label with nothing under
   * it has published an absence, which is an ordinary thing for a source to do and which the
   * aggregate holds as null. Refusing on the value would lose a real procedure over a field the
   * source simply left empty.
   */
  private void requireLabel(String label) {
    if (!labelled.containsKey(label)) {
      throw new LicitacionRecordUnavailableException(
          "Record %s publishes no %s".formatted(publicationId.value(), label));
    }
  }

  /**
   * The state as the source published it — its label, never a code. Two of the ten published codes
   * share a label, so this cannot be resolved back to one and the code comes from the listing.
   */
  private String stateLabel() {
    for (Element paragraph : document.select("p")) {
      Element emphasis = paragraph.selectFirst("em");
      if (emphasis == null || !Whitespace.strip(paragraph.ownText()).startsWith(STATE_LABEL)) {
        continue;
      }
      String label = PublishedValues.text(emphasis.wholeText());
      if (label != null) {
        return label;
      }
    }
    throw new LicitacionRecordUnavailableException(
        "Record %s publishes no %s".formatted(publicationId.value(), STATE_LABEL));
  }

  /**
   * Read whole rather than as jsoup's tidied {@code text()}, which collapses internal whitespace
   * runs and normalises newlines. A published object really does contain a double space, and
   * tidying it would store something the source did not publish.
   */
  private static @Nullable String textOf(@Nullable Element element) {
    return element == null ? null : element.wholeText();
  }
}
