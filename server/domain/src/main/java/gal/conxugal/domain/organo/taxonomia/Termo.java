package gal.conxugal.domain.organo.taxonomia;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A category in the Órganos taxonomy. {@code id} is system-assigned, {@code null} only
 * until the database assigns it on insert. A {@code null parentId} is a root; the taxonomy
 * is many-rooted and arbitrarily deep. The parent is held as the parent's id rather than a
 * nested term, keeping the single-table mapping of ADR-0008.
 */
@MappedEntity("termo")
public record Termo(
    @Id @GeneratedValue @Nullable TermoId id, String name, @Nullable TermoId parentId) {

  public Termo {
    Objects.requireNonNull(name, "name must not be null");
  }

  /** A term not yet persisted: the database assigns its id on insert. */
  public Termo(String name, @Nullable TermoId parentId) {
    this(null, name, parentId);
  }
}
