package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A licitación — a whole tender procedure — as the source published it. {@code id} is a
 * system-assigned identity, {@code null} only until the database assigns it;
 * {@code publicationId} is the source's own identifier, which is what matches this procedure
 * across imports and what makes a re-import an update in place rather than a duplicate.
 *
 * <p>The two are deliberately different things, and each has a type of its own so the compiler
 * says so. The publication identifier is a published value, and keying rows on it would put it in
 * every foreign key a procedure's children carry; the identity the system assigns costs one column
 * and keeps the published value out of six of them. See {@link PublicationId} for what it wraps
 * and why.
 *
 * <p>One consequence is worth naming rather than discovering: {@code SPEC-0006} R4's cross-family
 * tie-break on "the higher contract identifier" compares a licitación's against a contrato menor's
 * {@code long}, which is a comparison these two types no longer make for free. The task that first
 * feeds an operador name from a licitación owns that.
 *
 * <p>Only three things are required: the source identifier, the convening Órgano, and the state.
 * Every other value is nullable, and null means <em>the source published nothing there</em> — a
 * required field anywhere else would reject a real procedure over a blank the source left. A
 * publication date that could not be interpreted arrives here as null and is not a reason to
 * refuse the procedure.
 *
 * <p><strong>Four of its values come from the listing entry rather than from the record</strong>:
 * both dates and both halves of the state. The record publishes the state's label alone, which
 * for the two codes that share one is ambiguous, so whatever builds this aggregate needs the
 * listing entry beside the parsed record — or, on a retry, the values a ledger kept for it.
 *
 * <p><strong>The state and the three types are references, not columns here.</strong> Each is a
 * published vocabulary with its own row, so a value the source publishes on a thousand procedures
 * is held once and an unseen one costs nothing but a row. The three types are nullable because a
 * record need not publish them; the state is not, because the listing always does.
 *
 * <p>Nothing published is altered on the way in. Text arrives already trimmed at the adapter and
 * this aggregate stores what it is handed — no case folding, no collapsing of internal spacing,
 * no rounding, no truncation of its own, and no cap on the object's length. A value that carried
 * only whitespace is held as null rather than as an empty string, because it published nothing.
 *
 * <p>The two economic figures are different things and are labelled as such wherever either is
 * shown or totalled: the base budget includes VAT and the estimated value excludes it, which the
 * source states in the published text rather than the system asserting it. Neither is the
 * amount anything was awarded for — the awards live per lote, where the source publishes them.
 *
 * <p>{@code withdrawn} is declared so the aggregate is whole. Nothing an import does writes it: a
 * procedure absent from a later import is retained unchanged, because absence at the source is
 * not evidence of withdrawal, and the explicit removal that is meaningful belongs to a later
 * feature.
 *
 * <p>Nothing addresses the publication at the source, because nothing needs to: its address is
 * derivable from the {@code publicationId} this row already carries.
 */
@MappedEntity("licitacion")
public record Licitacion(
    @Id @GeneratedValue @Nullable LicitacionId id,
    PublicationId publicationId,
    OrganoId organoId,
    @Nullable LocalDate publicationDate,
    @Nullable LocalDate lastModified,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("state_id") LicitacionState state,
    @Nullable String expediente,
    @Nullable String obxecto,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("contract_type_id")
        @Nullable LicitacionContractType contractType,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("procedure_type_id")
        @Nullable LicitacionProcedureType procedureType,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("tramitacion_type_id")
        @Nullable LicitacionTramitacionType tramitacionType,
    @Nullable Integer loteCount,
    @Nullable Money baseBudget,
    @Nullable Money estimatedValue,
    boolean withdrawn) {

  public Licitacion {
    Objects.requireNonNull(publicationId, "publicationId must not be null");
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(state, "state must not be null");
    expediente = nullIfBlank(expediente);
    obxecto = nullIfBlank(obxecto);
  }

  /**
   * A procedure as it is first read from the source: the database assigns its id on insert, and
   * nothing an import does withdraws it.
   */
  public Licitacion(
      PublicationId publicationId,
      OrganoId organoId,
      @Nullable LocalDate publicationDate,
      @Nullable LocalDate lastModified,
      LicitacionState state,
      @Nullable String expediente,
      @Nullable String obxecto,
      @Nullable LicitacionContractType contractType,
      @Nullable LicitacionProcedureType procedureType,
      @Nullable LicitacionTramitacionType tramitacionType,
      @Nullable Integer loteCount,
      @Nullable Money baseBudget,
      @Nullable Money estimatedValue) {
    this(
        null,
        publicationId,
        organoId,
        publicationDate,
        lastModified,
        state,
        expediente,
        obxecto,
        contractType,
        procedureType,
        tramitacionType,
        loteCount,
        baseBudget,
        estimatedValue,
        false);
  }

  /**
   * Identity, not contents: two instances are the same procedure when they carry the same
   * assigned {@link LicitacionId}. The record's own equality would compare four joined vocabulary
   * rows too — so the same stored row, read twice with a corrected state label in between, would
   * compare unequal to itself, and two reads at different join depths would as well.
   *
   * <p>A procedure the database has not assigned an identity to is equal only to itself. There is
   * nothing to match it on: {@code publicationId} identifies it at the source, but two aggregates
   * holding one are two readings of the same publication, and deciding they are interchangeable
   * is the store's job at upsert, not this record's.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Licitacion licitacion && id != null && id.equals(licitacion.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }

  private static @Nullable String nullIfBlank(@Nullable String value) {
    return value == null || Whitespace.isBlank(value) ? null : value;
  }
}
