package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A party a contract has been awarded to, catalogued under the fiscal identifier the award
 * published. {@code id} is a separate system-assigned identity, {@code null} only until the
 * database assigns it on insert.
 *
 * <p>The identifier is held canonical — surrounding whitespace removed, letters upper-cased —
 * and that is the only value here reduced in any way. It is what the catalogue is matched on and
 * what is displayed, so an operador published as {@code b12345678} is held as {@code B12345678}
 * and the published case is retained nowhere. Everything else about it survives: internal
 * spacing and punctuation make a different identifier and therefore a different operador.
 *
 * <p>The {@code name}, by contrast, is exactly as the winning contract published it. Nothing
 * folds a name and nothing preserves an identifier's published case; the asymmetry is the point.
 *
 * <p>{@code nameRank} records which contract the name came from, so the choice survives the
 * contract going out of hand and two imports over the same data cannot disagree about it.
 * {@code nomesAlternativos} holds every <em>other</em> name the operador's contracts have
 * published — never the principal one, so promoting a name moves it between the two.
 *
 * <p>Nothing recorded here says whether the awardee is a natural person or a legal entity.
 */
@MappedEntity("operador_economico")
public record OperadorEconomico(
    @Id @GeneratedValue @Nullable OperadorId id,
    String fiscalId,
    String name,
    @Relation(Relation.Kind.EMBEDDED) @MappedProperty("name_rank") NomeRank nameRank,
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "operadorEconomicoId")
        Set<NomeAlternativo> nomesAlternativos) {

  public OperadorEconomico {
    Objects.requireNonNull(fiscalId, "fiscalId must not be null");
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(nameRank, "nameRank must not be null");
    Objects.requireNonNull(nomesAlternativos, "nomesAlternativos must not be null");
    fiscalId = fiscalId.strip().toUpperCase(Locale.ROOT);
    if (fiscalId.isEmpty()) {
      throw new IllegalArgumentException("fiscalId must not be empty once trimmed");
    }
    nomesAlternativos = Set.copyOf(nomesAlternativos);
    for (NomeAlternativo alternativo : nomesAlternativos) {
      if (alternativo.name().equals(name)) {
        throw new IllegalArgumentException(
            "nomesAlternativos must not hold the principal name: %s".formatted(name));
      }
      // The operador half of a retained name is half its identity, so a name filed against
      // another operador — or against none once this one has an id — is not this one's to hold.
      if (!Objects.equals(alternativo.operadorEconomicoId(), id)) {
        throw new IllegalArgumentException(
            "nomesAlternativos must be held against this operador: %s is held against %s"
                .formatted(alternativo.name(), alternativo.operadorEconomicoId()));
      }
    }
  }

  /** An operador no contract has named before: the database assigns its id on insert. */
  public OperadorEconomico(String fiscalId, String name, NomeRank nameRank) {
    this(null, fiscalId, name, nameRank, Set.of());
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
      return new OperadorEconomico(id, fiscalId, name, newRank, nomesAlternativos);
    }
    Set<NomeAlternativo> retained = new HashSet<>();
    for (NomeAlternativo alternativo : nomesAlternativos) {
      if (!alternativo.name().equals(newName)) {
        retained.add(alternativo);
      }
    }
    retained.add(new NomeAlternativo(id, name, nameRank));
    return new OperadorEconomico(id, fiscalId, newName, newRank, retained);
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
