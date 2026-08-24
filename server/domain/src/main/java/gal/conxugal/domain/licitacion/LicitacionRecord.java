package gal.conxugal.domain.licitacion;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One procedure as its published record states it, before it becomes a {@link Licitacion}. The
 * fields are named for the aggregate they feed rather than for the source's own labels, on
 * {@link gal.conxugal.domain.contrato.ContratoMenorSourceEntry ContratoMenorSourceEntry}'s
 * precedent — {@code expediente} is what the record labels {@code Referencia}.
 *
 * <p><strong>Four of the aggregate's values are not here, and cannot be.</strong> The record
 * publishes neither the last-modified date nor the state's <em>code</em>, and the code is what the
 * state is keyed on — two of the ten published codes already share a label, so the label alone
 * cannot be resolved back to one. Those come from the listing entry, or from the outstanding-record
 * ledger when a retry has no listing entry to read. The publication date the record does carry is
 * taken from the same two places, so that a retried procedure and a freshly walked one are built
 * from one source rather than two that could disagree.
 *
 * <p><strong>Only the state's label and the five tables are required.</strong> Every other value
 * is nullable and null means the record published nothing there. That is the ordinary case rather
 * than the exceptional one: the per-procedure page answers from an identifier space shared with
 * contratos menores, and {@code Nº lotes} and {@code Valor estimado} are simply absent on pages
 * that are not licitacións, while {@code Referencia} is absent on most that are.
 *
 * <p><strong>A table the record does not publish is an empty list, never null and never a
 * failure.</strong> Every <em>En curso</em>, <em>Pendente de adxudicar</em>, <em>Deserto</em>,
 * <em>Anulado</em> and <em>Renuncia</em> procedure has no resolution table at all — 278 of the
 * ~2 000 listing rows sampled — and a contrato menor served from the shared identifier space
 * publishes neither classification table nor a formalisation. A parse that read absence as failure
 * would send every one of them to the outstanding ledger. Only a table that is present and cannot
 * be read fails the record, and that judgement is the parse's.
 *
 * <p><strong>The awards and the lotes are separate answers to separate questions.</strong> An
 * award names the point it was made at; a lote is that point. A procedure can publish lotes it has
 * not awarded, and — measured on 822054 — can award lotes its {@code Relación de lotes} does not
 * list.
 *
 * <p>Text arrives trimmed at its ends and reduced no further, so a value's internal spacing is the
 * source's own. Nothing above this record trims, folds or truncates anything.
 *
 * @param publicationId the identifier the record was fetched by, which is what matches this
 *     procedure across imports
 * @param expediente the procedure's own reference, published as {@code Referencia}
 * @param obxecto what is being contracted
 * @param contractType {@code Tipo de contrato}, as published
 * @param procedureType {@code Tipo de procedemento}, as published
 * @param tramitacionType {@code Tipo de tramitación}, as published
 * @param loteCount {@code Nº lotes} — how many lotes the procedure declares, which is not
 *     necessarily how many its award table names
 * @param baseBudget {@code Orzamento base de licitación}, with the basis it was quoted on
 * @param estimatedValue {@code Valor estimado}, with the basis it was quoted on
 * @param stateLabel {@code Estado do procedemento} — the label alone, never a code
 * @param lotes the lotes the record's tables name, with whatever {@code Relación de lotes} adds
 * @param awards the resolution table's rows, one per award point
 * @param formalisations the formalisation table's rows, one per award point
 * @param cpvClassifications the CPV table's rows
 * @param nutClassifications the NUT table's rows
 */
public record LicitacionRecord(
    PublicationId publicationId,
    @Nullable String expediente,
    @Nullable String obxecto,
    @Nullable String contractType,
    @Nullable String procedureType,
    @Nullable String tramitacionType,
    @Nullable Integer loteCount,
    @Nullable PublishedAmount baseBudget,
    @Nullable PublishedAmount estimatedValue,
    String stateLabel,
    List<PublishedLote> lotes,
    List<PublishedAward> awards,
    List<PublishedFormalisation> formalisations,
    List<PublishedCpvClassification> cpvClassifications,
    List<PublishedNutClassification> nutClassifications) {

  public LicitacionRecord {
    Objects.requireNonNull(publicationId, "publicationId must not be null");
    Objects.requireNonNull(stateLabel, "stateLabel must not be null");
    lotes = List.copyOf(lotes);
    awards = List.copyOf(awards);
    formalisations = List.copyOf(formalisations);
    cpvClassifications = List.copyOf(cpvClassifications);
    nutClassifications = List.copyOf(nutClassifications);
  }
}
