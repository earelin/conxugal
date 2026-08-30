package gal.conxugal.domain.operador;

import gal.conxugal.domain.licitacion.LicitacionId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import java.util.Objects;

/**
 * One member firm of a consortium, related to the consortium itself.
 *
 * <p><strong>Both ends are operadores</strong>, which is what lets this be read in either
 * direction: the members of one UTE, and the UTEs one firm has belonged to, are the same relation
 * asked from opposite sides. One shape serves a consortium the source identified — one catalogue
 * entry across every procedure naming it — and one it did not, catalogued per bid, without either
 * branch needing a shape of its own.
 *
 * <p><strong>The procedure that stated it is part of the row, and it is what makes an identified
 * UTE reconcilable at all.</strong> Such a UTE is one operador across every procedure naming it, so
 * two of them publish two member lists; when the first stops stating a member, the pair alone
 * cannot say whether the second still does. Withdrawing it would hide a fact the second publishes,
 * and keeping it would show one nothing publishes. Holding the statement per procedure lets a
 * reconciliation withdraw its own and no one else's, and the relation a reader is shown is the pair
 * any visible statement still makes. For a consortium the source declines to identify every
 * statement is the one procedure's anyway, so the two branches keep one code path.
 *
 * <p>It is the one thing here that points at the licitacións package, which otherwise points only
 * this way. That is accepted rather than overlooked: the relation belongs beside the catalogue
 * whose reachability counts it, and moving it would reverse the arrow rather than remove it the
 * moment a reachability read exists.
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
 * <p>This is a value rather than an entity of its own: the triple it carries is what the table is
 * keyed on, which is what makes a member stored once however many times one procedure lists it. It
 * holds no identity of its own, so it compares by its components as the record does by default.
 *
 * <p><strong>That equality is the quadruple and not the triple</strong>, and the difference is
 * worth naming: two instances carrying one triple and different withdrawal markers are the same
 * stored row and compare unequal all the same. A caller that collects memberships into a set while
 * a reconciliation flips markers would hold one row twice, and reaching for the triple alone is not
 * open to this record: every component the table keys on is another aggregate's identifier, so an
 * equality override here is what the architecture rule forbids.
 *
 * <p><strong>Reading either direction now answers one row per procedure</strong>, so a pair two
 * procedures both state appears twice. That is the honest shape of a per-procedure statement, and
 * it costs the predicate nothing — reachability asks whether <em>any</em> visible membership
 * remains — but a reader wanting distinct consortia has to fold them.
 */
@MappedEntity("operador_ute_membership")
public record UteMembership(
    @Id OperadorId uteId,
    @Id @MappedProperty("operador_economico_id") OperadorId operadorId,
    @Id LicitacionId licitacionId,
    boolean withdrawn) {

  public UteMembership {
    Objects.requireNonNull(uteId, "uteId must not be null");
    Objects.requireNonNull(operadorId, "operadorId must not be null");
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    if (uteId.equals(operadorId)) {
      throw new IllegalArgumentException(
          "a consortium is not a member of itself: %s".formatted(uteId));
    }
  }

  /**
   * A membership as one procedure first states it: nothing an import does withdraws it. Both
   * operadores are already catalogued, since neither end can be related before it exists, and so is
   * the procedure stating it.
   */
  public UteMembership(OperadorId uteId, OperadorId operadorId, LicitacionId licitacionId) {
    this(uteId, operadorId, licitacionId, false);
  }
}
