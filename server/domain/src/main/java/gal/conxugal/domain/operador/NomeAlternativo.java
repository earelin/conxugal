package gal.conxugal.domain.operador;

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
 * <p>{@code operadorEconomicoId} is {@code null} only until the operador it belongs to has been
 * assigned an identity.
 */
@MappedEntity("operador_economico_nome_alternativo")
public record NomeAlternativo(
    @Nullable OperadorId operadorEconomicoId,
    String name,
    @Relation(Relation.Kind.EMBEDDED) @MappedProperty("last_published") NomeRank lastPublished) {

  public NomeAlternativo {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(lastPublished, "lastPublished must not be null");
  }

  /** A name retained before the operador it belongs to has been assigned an identity. */
  public NomeAlternativo(String name, NomeRank lastPublished) {
    this(null, name, lastPublished);
  }
}
