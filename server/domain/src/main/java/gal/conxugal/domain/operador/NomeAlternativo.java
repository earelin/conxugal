package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A name an operador has been published under other than the one it is displayed as, with the
 * rank of the most recent contract that published it. One value per distinct name however many
 * contracts publish it, so an operador with ten thousand contracts under one name retains one.
 *
 * <p>Distinctness is by the name exactly as published: two spellings differing in letter case or
 * internal spacing are two names. Nothing here reduces a name the way an operador's fiscal
 * identifier is reduced — that reduction exists for identifiers and never for names.
 *
 * <p>This is a value inside the {@link OperadorEconomico} aggregate, not an entity of its own:
 * <b>the name is the identity</b>, which is what makes a name retained once. {@code
 * operadorEconomicoId} is the column that files the row under its operador — it completes the
 * table's key, not the value's, and is {@code null} until the operador has been assigned an
 * identity.
 */
@MappedEntity("operador_economico_nome_alternativo")
public record NomeAlternativo(
    @Id @Nullable OperadorId operadorEconomicoId,
    @Id String name,
    @Relation(Relation.Kind.EMBEDDED) @MappedProperty("last_published") NomeRank lastPublished) {

  public NomeAlternativo {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(lastPublished, "lastPublished must not be null");
  }

  /**
   * The name alone, so the same name published again by a later contract is the same retained
   * name at a new rank and a {@code Set} holds it once — which one's rank survives the collapse
   * is the caller's to decide before building the set. Neither the rank nor the operador column
   * enters into it: the aggregate a value sits in is already the operador it belongs to, and
   * nothing compares values across two of them.
   */
  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof NomeAlternativo other && name.equals(other.name));
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
