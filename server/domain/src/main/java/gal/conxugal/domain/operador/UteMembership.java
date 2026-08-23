package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import java.util.Objects;

/**
 * One member firm of a consortium, related to the consortium itself.
 *
 * <p><strong>Both ends are operadores</strong>, which is what lets this be read in either
 * direction: the members of one UTE, and the UTEs one firm has belonged to, are the same relation
 * asked from opposite sides. It holds no reference to any contract, so it serves a consortium the
 * source identified — one catalogue entry across every procedure naming it — and one it did not,
 * catalogued per bid, without either branch needing a shape of its own.
 *
 * <p>Members are always catalogueable: all 80 member entries measured carried an ordinary fiscal
 * identifier, none a dash and none a placeholder. A member whose identifier is unusable yields no
 * operador and therefore no membership, leaving the consortium and its other members untouched.
 *
 * <p><strong>It carries its own withdrawal marker</strong> so a membership can stop being visible
 * without being erased. One visible membership is enough to keep an operador reachable, so a
 * member firm whose only tie is a membership no visible bid still publishes would otherwise stay
 * reachable through a fact no reader is shown. Keeping the two in step is the reconciliation's job;
 * the marker is here so it has somewhere to write.
 *
 * <p>This is a value rather than an entity of its own: the pair it carries is what the table is
 * keyed on, which is what makes a member stored once however many times the source lists it. It
 * holds no identity of its own, so it compares by its components as the record does by default.
 *
 * <p><strong>That equality is the triple and not the pair</strong>, and the difference is worth
 * naming: two instances carrying one pair and different withdrawal markers are the same stored row
 * and compare unequal all the same. A caller that collects memberships into a set while a
 * reconciliation flips markers would hold one row twice, and reaching for the pair alone is not
 * open to this record: every component the table keys on is another aggregate's identifier, so an
 * equality override here is what the architecture rule forbids.
 */
@MappedEntity("operador_ute_membership")
public record UteMembership(
    @Id OperadorId uteId,
    @Id @MappedProperty("operador_economico_id") OperadorId operadorId,
    boolean withdrawn) {

  public UteMembership {
    Objects.requireNonNull(uteId, "uteId must not be null");
    Objects.requireNonNull(operadorId, "operadorId must not be null");
    if (uteId.equals(operadorId)) {
      throw new IllegalArgumentException(
          "a consortium is not a member of itself: %s".formatted(uteId));
    }
  }

  /**
   * A membership as it is first read from the source: nothing an import does withdraws it. Both
   * operadores are already catalogued, since neither end can be related before it exists.
   */
  public UteMembership(OperadorId uteId, OperadorId operadorId) {
    this(uteId, operadorId, false);
  }
}
