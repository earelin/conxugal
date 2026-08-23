package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.OperadorId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import java.util.Objects;

/**
 * One member firm of a consortium that bid, tied to the bid rather than to the consortium.
 *
 * <p><strong>It hangs off the participation, never off a UTE operador</strong>, and that is what
 * lets one shape serve both branches: a consortium the source identified (2 of 35 measured) and
 * one it did not (the other 33) store the same membership rows, and only the participation's
 * operador reference differs between them. A membership keyed on an operador-to-operador pair
 * could not express the 94% case at all, because the consortium half of that pair does not exist.
 *
 * <p>Members are always catalogueable: all 80 member entries measured carried an ordinary fiscal
 * identifier, none a dash and none a placeholder. So the member is a reference to an operador and
 * holds no name of its own — the exception to that rule is the consortium's published name, which
 * lives on {@link Participation}.
 *
 * <p><strong>It carries its own withdrawal marker</strong> so a membership's visibility can follow
 * its participation's. A member firm whose only tie to a procedure is a membership under a
 * withdrawn participation would otherwise stay reachable through an invisible fact. Keeping the
 * two in step is the reconciliation's job; the marker is here so it has somewhere to write.
 *
 * <p>This is a value filed under its participation rather than an entity of its own: the pair it
 * carries is what the table is keyed on, which is what makes a member stored once however many
 * times the source lists it. It holds no identity of its own, so it compares by its components as
 * the record does by default.
 *
 * <p><strong>That equality is the triple and not the pair</strong>, and the difference is worth
 * naming: two instances carrying one pair and different withdrawal markers are the same stored row
 * and compare unequal all the same. Nothing this task builds notices, because nothing constructs
 * both readings — but a caller that collects memberships into a set while a reconciliation flips
 * markers would hold one row twice, and reaching for the pair alone is not open to this record:
 * every component the table keys on is another aggregate's identifier, so an equality override
 * here is what the architecture rule forbids. If a caller ever needs the pair alone, that is a
 * change to the rule rather than to this record.
 */
@MappedEntity("licitacion_ute_membership")
public record UteMembership(
    @Id ParticipationId participationId,
    @Id @MappedProperty("operador_economico_id") OperadorId operadorId,
    boolean withdrawn) {

  public UteMembership {
    Objects.requireNonNull(participationId, "participationId must not be null");
    Objects.requireNonNull(operadorId, "operadorId must not be null");
  }

  /**
   * A membership as it is first read from the source: nothing an import does withdraws it. The
   * participation it hangs off already carries an identity, since a membership cannot be filed
   * before the bid it belongs to.
   */
  public UteMembership(ParticipationId participationId, OperadorId operadorId) {
    this(participationId, operadorId, false);
  }
}
