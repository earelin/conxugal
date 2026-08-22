package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.OperadorId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One published bidder for one award point: who bid, on which lote, and whether they won.
 * {@code id} is a system-assigned identity, {@code null} only until the database assigns it, and
 * it is what a {@link UteMembership} is filed under.
 *
 * <p><strong>Four shapes are expressible, and all four occur.</strong> A single firm with an
 * operador is the ordinary one — 578 of 613 measured bidder rows. A single firm without one is the
 * row whose published identifier was unusable, recorded as neither participant nor awardee of any
 * catalogue entry rather than dropped. A consortium with an operador is the UTE the source
 * identified, 2 of 35. A consortium with a published name and no operador is the other 33: a UTE
 * carrying {@code -} or a {@code TEMP-…} placeholder, which is no identity at all.
 *
 * <p><strong>The consortium marker is structural, not a reading of the name.</strong> A consortium
 * nests a second list inside its bidder cell, and over 613 rows that nesting never appeared on a
 * single firm and never failed to appear on a consortium. The name is not the test: 7 of 35
 * consortia are published under a name that does not begin with <em>UTE</em>. Detection itself is
 * a later task's; this record carries the fact it establishes.
 *
 * <p><strong>The published consortium name is this family's one exception to holding no name of
 * its own.</strong> Every other party is a reference to an operador, and a name belongs on the
 * operador an identifier resolves to. An unidentified consortium has no such operador, so the
 * alternative to holding its published name here is losing it. It is exactly one component on
 * exactly one record, and it is null wherever the catalogue could have held the party — which is
 * refused here rather than left to the column constraint, so the diagnosis names the mistake
 * instead of naming a column, and a parse defect does not cost the whole procedure.
 *
 * <p><strong>The refusal is one-directional, and deliberately so.</strong> A name requires a
 * consortium with no operador; a consortium with <em>no</em> name is accepted, because nothing
 * measured guarantees every consortium's cell carries one and refusing that row would lose a real
 * bid. It also leaves room for a consortium that gains an operador once a formalisation identifies
 * it, which clears the name in the same write.
 *
 * <p>{@code won} carries the award back to the bid that won it, so an operador that won one lote
 * of a procedure and lost another holds a row for each. {@code withdrawn} is the marker R13's
 * retention needs; nothing here writes it.
 */
@MappedEntity("licitacion_participation")
public record Participation(
    @Id @GeneratedValue @Nullable ParticipationId id,
    LicitacionId licitacionId,
    @Nullable LoteId loteId,
    @Nullable OperadorId operadorEconomicoId,
    boolean won,
    boolean consortium,
    @Nullable String consortiumName,
    boolean withdrawn) {

  public Participation {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    consortiumName = PublishedText.orNullWhenBlank(consortiumName);
    if (consortiumName != null && (!consortium || operadorEconomicoId != null)) {
      throw new IllegalArgumentException(
          "consortiumName is only for a consortium the catalogue could not hold, so it requires "
              + "consortium and no operadorEconomicoId");
    }
  }

  /**
   * A participation as it is first read from the source: the database assigns its id on insert,
   * and nothing an import does withdraws it.
   */
  public Participation(
      LicitacionId licitacionId,
      @Nullable LoteId loteId,
      @Nullable OperadorId operadorEconomicoId,
      boolean won,
      boolean consortium,
      @Nullable String consortiumName) {
    this(null, licitacionId, loteId, operadorEconomicoId, won, consortium, consortiumName, false);
  }

  /**
   * Identity, not contents: two instances are the same participation when they carry the same
   * assigned {@link ParticipationId}. This is the equality the consortium branch needs — a
   * consortium the formalisation identifies gains an operador and loses its published name after
   * its participation was written from the bidder row, and it is the same bid throughout.
   *
   * <p>A participation the database has not assigned an identity to is equal only to itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Participation participation
        && id != null
        && id.equals(participation.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
