package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.OperadorId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One published bidder for one award point: who bid, on which lote, and whether they won.
 * {@code id} is a system-assigned identity, {@code null} only until the database assigns it.
 *
 * <p><strong>It holds a reference to the party and no copy of its name</strong>, and there is no
 * exception. Every bidder the source publishes — a single firm, a member firm, a consortium —
 * resolves to an operador, and a name belongs on the operador rather than repeated per row. A
 * consortium the source declines to identify is catalogued too, under the bid that published it,
 * so its published name lives where every other party's does.
 *
 * <p>{@code operadorEconomicoId} is null for one reason only: the party's published identifier was
 * unusable, so it resolved to nobody and is recorded as neither participant nor awardee of any
 * catalogue entry rather than dropped. That is 578-of-613's exception, not the consortium case,
 * which an earlier model conflated with it.
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
    boolean withdrawn) {

  public Participation {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
  }

  /**
   * A participation as it is first read from the source: the database assigns its id on insert,
   * and nothing an import does withdraws it.
   */
  public Participation(
      LicitacionId licitacionId,
      @Nullable LoteId loteId,
      @Nullable OperadorId operadorEconomicoId,
      boolean won) {
    this(null, licitacionId, loteId, operadorEconomicoId, won, false);
  }

  /**
   * Identity, not contents: two instances are the same participation when they carry the same
   * assigned {@link ParticipationId}. This is the equality a restatement needs — a bid whose
   * award marker moves is the same bid throughout.
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
