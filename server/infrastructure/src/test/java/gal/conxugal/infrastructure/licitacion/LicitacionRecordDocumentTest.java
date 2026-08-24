package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAmount;
import gal.conxugal.domain.licitacion.VatBasis;
import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/**
 * The reading of a page, on markup small enough to state what each case is about. What the real
 * source publishes is the integration test's, against the captured record itself.
 */
class LicitacionRecordDocumentTest {

  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");

  /**
   * The state as the page publishes it, behind a paragraph that carries an emphasised value and is
   * not the state.
   *
   * <p><strong>The decoy is the point.</strong> The state is reached by a document-wide scan, so
   * without one ahead of it a parse that had stopped reading the label at all — taking the first
   * emphasised value anywhere on the page — would answer correctly here and store a wrong state as
   * authoritative on a real record. It is the only required field, and it was the only one of the
   * four without a decoy.
   */
  private static final String STATE =
      """
      <p>Órgano de contratación <em>Axencia Turismo de Galicia</em></p>
      <div class="col-md-6 text-right">
        <p>
          Estado do procedemento:
          <em>Formalizado</em>
        </p>
      </div>
      """;

  /** Every label the procedure's own block carries, in the order the source publishes them. */
  private static final String EVERY_LABEL =
      """
      <dt>Obxecto</dt><dd>Mellora de infraestruturas</dd>
      <dt>Tipo de contrato</dt><dd>Obras</dd>
      <dt>Tipo de procedemento</dt><dd>Abertos</dd>
      <dt>Tipo de tramitación</dt><dd>Ordinaria</dd>
      <dt>Nº lotes</dt><dd>2</dd>
      <dt>Orzamento base de licitación</dt><dd>3.378.552,09 con IVE</dd>
      <dt>Valor estimado</dt><dd>2.792.191,81 sen IVE</dd>
      """;

  /** The same seven pairs, published in the opposite order. */
  private static final String EVERY_LABEL_REVERSED =
      """
      <dt>Valor estimado</dt><dd>2.792.191,81 sen IVE</dd>
      <dt>Orzamento base de licitación</dt><dd>3.378.552,09 con IVE</dd>
      <dt>Nº lotes</dt><dd>2</dd>
      <dt>Tipo de tramitación</dt><dd>Ordinaria</dd>
      <dt>Tipo de procedemento</dt><dd>Abertos</dd>
      <dt>Tipo de contrato</dt><dd>Obras</dd>
      <dt>Obxecto</dt><dd>Mellora de infraestruturas</dd>
      """;

  @Test
  void reads_every_labelled_scalar_the_record_publishes() {
    LicitacionRecord record = read(STATE + mainBlock(EVERY_LABEL));

    assertThat(record)
        .extracting(
            LicitacionRecord::obxecto,
            LicitacionRecord::contractType,
            LicitacionRecord::procedureType,
            LicitacionRecord::tramitacionType,
            LicitacionRecord::loteCount,
            LicitacionRecord::stateLabel)
        .containsExactly(
            "Mellora de infraestruturas", "Obras", "Abertos", "Ordinaria", 2, "Formalizado");
    assertThat(record.baseBudget())
        .isEqualTo(
            new PublishedAmount(new Money(new BigDecimal("3378552.09")), VatBasis.INCLUSIVE));
    assertThat(record.estimatedValue())
        .isEqualTo(
            new PublishedAmount(new Money(new BigDecimal("2792191.81")), VatBasis.EXCLUSIVE));
  }

  /**
   * The criterion this reading exists for: nothing here counts pairs or indexes a list, so the
   * order the source happens to publish its fields in carries no meaning. One value is pinned
   * before the two are compared, because two records that both parsed to nothing would otherwise
   * be equal and the comparison would say so approvingly.
   */
  @Test
  void reads_the_same_scalars_when_the_labelled_pairs_are_reordered() {
    LicitacionRecord published = read(STATE + mainBlock(EVERY_LABEL));
    LicitacionRecord reordered = read(STATE + mainBlock(EVERY_LABEL_REVERSED));

    assertThat(reordered.obxecto()).isEqualTo("Mellora de infraestruturas");
    assertThat(reordered).isEqualTo(published);
  }

  /**
   * The page publishes {@code Obxecto} twice more, empty, in blocks about framework agreements and
   * dynamic purchasing systems. A document-wide lookup would answer with whichever it reached.
   *
   * <p><strong>The decoy is placed before the procedure's own block on purpose.</strong> The
   * lookup takes the first occurrence in document order, so a decoy that came second could never
   * win and this test would pass with the container scope deleted — proving nothing. Ordered this
   * way it fails the moment the scope goes, which is the regression it exists to catch.
   */
  @Test
  void reads_the_object_from_the_main_block_and_not_the_empty_copies_beside_it() {
    String elsewhere =
        """
        <div class="row"><dl><dt>Obxecto</dt><dd>   </dd></dl></div>
        """;

    LicitacionRecord record = read(STATE + elsewhere + mainBlock(EVERY_LABEL));

    assertThat(record.obxecto()).isEqualTo("Mellora de infraestruturas");
  }

  /**
   * The restructuring panel restates both economic labels with a trailing colon, and a lookup
   * matching loosely would take the panel's copy for the procedure's own figure.
   *
   * <p><strong>Exact matching is what separates them here, not the container.</strong> The trailing
   * colon makes a different key, so this passes with the container scope removed — the object's
   * decoy is what covers that. Recorded because the two protections are easy to conflate, and a
   * comment claiming this test covers both would be worse than no comment.
   */
  @Test
  void does_not_read_the_budget_from_the_restated_label_ending_in_colon() {
    String restated =
        """
        <div class="row">
          <dl>
            <dt class="detalleReestructuracion">Orzamento base de licitación:</dt>
            <dd>99,99 con IVE</dd>
          </dl>
        </div>
        """;

    LicitacionRecord record = read(STATE + restated + mainBlock(EVERY_LABEL));

    assertThat(record.baseBudget())
        .extracting(PublishedAmount::amount)
        .isEqualTo(new Money(new BigDecimal("3378552.09")));
  }

  /**
   * The page publishes a second, near-identical contract-type label that means something else, and
   * unlike the other decoys it sits <em>inside</em> the procedure's own block, so the container
   * scope is no help against it. Exact matching is what separates them — placed first here so that
   * a lookup which stopped being exact answers with the wrong one.
   */
  @Test
  void does_not_read_the_contract_type_from_the_rexime_xuridico_label() {
    String withRegime =
        mainBlock(
            "<dt>Tipo de contrato(réxime xurídico)</dt><dd>No aplica</dd>" + EVERY_LABEL);

    LicitacionRecord record = read(STATE + withRegime);

    assertThat(record.contractType()).isEqualTo("Obras");
  }

  /**
   * Criterion 44, on the shape that breaks it: a value carrying a real double space and a line
   * break keeps both, and loses only the markup's own padding around it.
   */
  @Test
  void keeps_the_internal_spacing_and_trims_only_the_ends() {
    String spaced =
        mainBlock("<dt>Obxecto</dt><dd>\n  dentro do  programa\nde impulso  \n</dd>");

    LicitacionRecord record = read(STATE + spaced);

    assertThat(record.obxecto()).isEqualTo("dentro do  programa\nde impulso");
  }

  @Test
  void answers_nothing_for_the_value_that_is_empty_once_trimmed() {
    LicitacionRecord record = read(STATE + mainBlock("<dt>Obxecto</dt><dd>   \n  </dd>"));

    assertThat(record.obxecto()).isNull();
  }

  /** The count sits beside a link and an icon, and neither is part of the value. */
  @Test
  void reads_the_lote_count_past_the_link_the_source_puts_beside_it() {
    String withLink =
        mainBlock(
            """
            <dt>Obxecto</dt><dd>Mellora</dd>
            <dt>Nº lotes</dt>
            <dd>
              2
              <a href="#" onclick="mostrarInfoLotes();" title="Consultar lotes">
                <span class="glyphicon glyphicon-list-alt" aria-hidden="true"></span>
              </a>
            </dd>
            """);

    LicitacionRecord record = read(STATE + withLink);

    assertThat(record.loteCount()).isEqualTo(2);
  }

  /**
   * The page is served from an identifier space shared with contratos menores, whose records carry
   * neither label. Absence is an ordinary answer and must not cost the whole record.
   */
  @Test
  void answers_nothing_for_the_labels_contrato_menor_records_omit() {
    LicitacionRecord record = read(STATE + mainBlock("<dt>Obxecto</dt><dd>Subministración</dd>"));

    assertThat(record)
        .extracting(
            LicitacionRecord::loteCount,
            LicitacionRecord::estimatedValue,
            LicitacionRecord::expediente)
        .containsOnlyNulls();
  }

  /**
   * By its label, and not as the block's first {@code <dd>} — so a pair the source adds ahead of it
   * cannot become the procedure's reference.
   */
  @Test
  void reads_the_reference_by_its_label_rather_than_by_its_position() {
    String reference =
        """
        <div class="infoReferencia">
          <dl>
            <dt>Rexistro</dt><dd>irrelevante</dd>
            <dt>Referencia</dt><dd>ATG-2026-0098</dd>
          </dl>
        </div>
        """;

    LicitacionRecord record = read(STATE + reference + mainBlock(EVERY_LABEL));

    assertThat(record.expediente()).isEqualTo("ATG-2026-0098");
  }

  /**
   * An unknown identifier answers 200 with the site's error page, so this — not a status — is the
   * whole of the not-found path.
   */
  @Test
  void throws_when_the_page_carries_no_procedure_block() {
    assertThatThrownBy(() -> read("<html><body><p>Erro</p></body></html>"))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("822054")
        .hasNoCause();
  }

  /**
   * The state is reached by scanning the page rather than by a labelled pair, so its label is the
   * only thing separating it from any other emphasised value — and the page carries several.
   */
  @Test
  void reads_the_state_from_the_paragraph_that_carries_its_label() {
    LicitacionRecord record = read(STATE + mainBlock(EVERY_LABEL));

    assertThat(record.stateLabel()).isEqualTo("Formalizado");
  }

  /**
   * First occurrence wins, so a repeated label reads as the source's first statement of it. Without
   * this the map would answer with whichever copy the page happened to end on.
   */
  @Test
  void reads_the_first_value_when_the_block_repeats_the_label() {
    String repeated =
        mainBlock("<dt>Obxecto</dt><dd>Primeira</dd><dt>Obxecto</dt><dd>Segunda</dd>");

    LicitacionRecord record = read(STATE + repeated);

    assertThat(record.obxecto()).isEqualTo("Primeira");
  }

  /**
   * The label is reduced the way every other published value is. {@code String.strip} leaves a
   * non-breaking space in place, and the page carries dozens of them — a label padded with one
   * would key an entry no lookup could reach, reading the field as absent with nothing to say why.
   */
  @Test
  void reads_the_value_when_the_label_is_padded_with_non_breaking_spaces() {
    String padded =
        mainBlock("<dt>&nbsp;Obxecto&nbsp;</dt><dd>Mellora</dd>");

    LicitacionRecord record = read(STATE + padded);

    assertThat(record.obxecto()).isEqualTo("Mellora");
  }

  /**
   * The markup already wraps this field's <em>value</em> across four lines, so a template that
   * wrapped its label too is not a hypothetical. A label read as published would key an entry no
   * lookup reaches, and the budget would be stored absent from a record that published one.
   */
  @Test
  void reads_the_value_when_the_markup_wraps_the_label_across_lines() {
    String wrapped =
        mainBlock(
            """
            <dt>Obxecto</dt><dd>Mellora</dd>
            <dt>Orzamento base de
                licitación</dt><dd>3.378.552,09 con IVE</dd>
            """);

    LicitacionRecord record = read(STATE + wrapped);

    assertThat(record.baseBudget())
        .extracting(PublishedAmount::amount)
        .isEqualTo(new Money(new BigDecimal("3378552.09")));
  }

  /**
   * A label the source published with no value under it has published an absence, which is an
   * ordinary thing to do. Refusing the record over it would strand a real procedure in the
   * outstanding ledger for ever — and this page's templates do emit a {@code <dt>} conditionally.
   */
  @Test
  void answers_nothing_for_the_label_the_source_left_without_any_value() {
    String orphan =
        mainBlock("<dt>Obxecto</dt><dt>Tipo de contrato</dt><dd>Obras</dd>");

    LicitacionRecord record = read(STATE + orphan);

    assertThat(record.obxecto()).isNull();
    assertThat(record.contractType()).isEqualTo("Obras");
  }

  @Test
  void throws_when_the_record_publishes_no_state() {
    assertThatThrownBy(() -> read(mainBlock(EVERY_LABEL)))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("Estado do procedemento");
  }

  @Test
  void throws_when_the_record_publishes_no_object_label() {
    assertThatThrownBy(() -> read(STATE + mainBlock("<dt>Tipo de contrato</dt><dd>Obras</dd>")))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("Obxecto");
  }

  private static LicitacionRecord read(String html) {
    return LicitacionRecordDocument.read(PUBLICATION_ID, Jsoup.parse(html, "/licitacion"));
  }

  private static String mainBlock(String pairs) {
    return
        """
        <div class="row infoConcurso">
          <div class="col-md-12"><dl>%s</dl></div>
        </div>
        """
        .formatted(pairs);
  }
}
