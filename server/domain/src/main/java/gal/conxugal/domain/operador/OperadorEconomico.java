package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A party a contract has been awarded to, catalogued under the fiscal identifier the award
 * published. {@code id} is a separate system-assigned identity, {@code null} only until the
 * database assigns it on insert.
 *
 * <p>The identifier is a {@link FiscalIdentifier}, which is canonical by construction and the
 * only value here reduced in any way. It is what the catalogue is matched on and what is
 * displayed, so an operador published as {@code b12345678} is held as {@code B12345678} and the
 * published case is retained nowhere. Whether an award yields an operador at all is asked of that
 * type before one is built, not of this record.
 *
 * <p><strong>It is null for one party and one only: a UTE the source declines to identify.</strong>
 * A <em>unión temporal de empresas</em> is named, structured and listed the members of while
 * carrying no identifier of its own on 33 of 35 measured rows, and it is catalogued under the bid
 * that published it instead. A null here is therefore <em>this party has no published
 * identifier</em>, never <em>the identifier was unusable</em> — that case yields no operador at
 * all, and never one holding a placeholder. Because such a row is matched on nothing, it can
 * neither absorb another party's contract nor be re-partitioned once written; the price is that
 * two bids by what a reader would call one consortium are two operadores.
 *
 * <p>The {@code name}, by contrast, is exactly as the winning contract published it. Nothing
 * folds a name and nothing preserves an identifier's published case; the asymmetry is the point.
 *
 * <p>{@code nameRank} records which contract the name came from, so the choice survives the
 * contract going out of hand and two imports over the same data cannot disagree about it.
 * {@code nomesAlternativos} holds every <em>other</em> name the operador's contracts have
 * published — never the principal one, so promoting a name moves it between the two.
 *
 * <p>{@code ute} is the one kind this record carries. Nothing here says whether the awardee is a
 * natural person or a legal entity — that would have to be derived from the shape of an
 * identifier — while being a consortium is something the source publishes structurally, and a
 * fact about a group of firms rather than about a person.
 */
@MappedEntity("operador_economico")
public record OperadorEconomico(
    @Id @GeneratedValue @Nullable OperadorId id,
    @Nullable FiscalIdentifier fiscalId,
    String name,
    boolean ute,
    @Relation(Relation.Kind.EMBEDDED) @MappedProperty("name_rank") NomeRank nameRank,
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "operadorEconomicoId")
        Set<NomeAlternativo> nomesAlternativos) {

  public OperadorEconomico {
    Objects.requireNonNull(name, "name must not be null");
    if (fiscalId == null && !ute) {
      throw new IllegalArgumentException(
          "only a UTE the source declines to identify is catalogued without a fiscal identifier: "
              + name);
    }
    Objects.requireNonNull(nameRank, "nameRank must not be null");
    Objects.requireNonNull(nomesAlternativos, "nomesAlternativos must not be null");
    nomesAlternativos = Set.copyOf(nomesAlternativos);
    for (NomeAlternativo alternativo : nomesAlternativos) {
      if (alternativo.name().equals(name)) {
        throw new IllegalArgumentException(
            "nomesAlternativos must not hold the principal name: %s".formatted(name));
      }
    }
  }

  /** An operador no contract has named before: the database assigns its id on insert. */
  public OperadorEconomico(FiscalIdentifier fiscalId, String name, NomeRank nameRank) {
    this(null, fiscalId, name, false, nameRank, Set.of());
  }

  /**
   * A UTE the source declines to identify, catalogued under the bid that published it. It holds no
   * fiscal identifier, so nothing will ever find it again by one — which is what keeps it from
   * absorbing another party's contract, and what makes a second bid by a similarly-named
   * consortium a second operador rather than this one.
   */
  public static OperadorEconomico unidentifiedUte(String name, NomeRank nameRank) {
    return new OperadorEconomico(null, null, name, true, nameRank, Set.of());
  }

  /** A UTE the source publishes an identifier for: an ordinary catalogue entry, marked as one. */
  public static OperadorEconomico identifiedUte(
      FiscalIdentifier fiscalId, String name, NomeRank nameRank) {
    Objects.requireNonNull(fiscalId, "fiscalId must not be null");
    return new OperadorEconomico(null, fiscalId, name, true, nameRank, Set.of());
  }

  /**
   * The operador displayed under a name and the rank it was taken from — the two move together
   * or not at all, so no row can remember a name from one contract and a rank from another. The
   * name being displaced is retained as an alternative under the rank it won with, and a name
   * already retained under the incoming one stops being an alternative. A contract republishing
   * the name already displayed advances only the rank and retains nothing.
   *
   * <p>Whether the contract supplying these outranks the incumbent is decided before this is
   * called; this only makes the two inseparable once it has been.
   */
  public OperadorEconomico displaying(String newName, NomeRank newRank) {
    if (name.equals(newName)) {
      return new OperadorEconomico(id, fiscalId, name, ute, newRank, nomesAlternativos);
    }
    Set<NomeAlternativo> retained = new HashSet<>();
    for (NomeAlternativo alternativo : nomesAlternativos) {
      if (!alternativo.name().equals(newName)) {
        retained.add(alternativo);
      }
    }
    retained.add(new NomeAlternativo(id, name, nameRank));
    return new OperadorEconomico(id, fiscalId, newName, ute, newRank, retained);
  }

  /**
   * Identity, not value, so equality never walks the retained names. An operador the database has
   * not yet assigned an id to is equal to nothing but itself.
   */
  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof OperadorEconomico other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
