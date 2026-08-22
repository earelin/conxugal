package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

/**
 * A kind of tramitación a licitación can be processed under — <em>Ordinaria</em>,
 * <em>Urxente</em> — as the record's {@code Tipo de tramitación} publishes it. {@code id} is a
 * system-assigned identity, {@code null} only until the database assigns it.
 *
 * <p><strong>The published name is the source's identifier here</strong>, and the natural key a
 * re-import matches on: this vocabulary is published as a bare label, with no code on the record
 * and no type field in the listing. Nothing here normalises it, so two published spellings are two
 * entries.
 *
 * <p>The name is required, and a blank or untrimmed one is refused — see {@link VocabularyName}.
 * A procedure whose record published no tramitación type refers to none at all.
 *
 * @see LicitacionContractType for the reasoning in full — the three type vocabularies share it
 */
@MappedEntity("licitacion_tramitacion_type")
public record LicitacionTramitacionType(
    @Id @GeneratedValue @Nullable LicitacionTramitacionTypeId id, String name) {

  public LicitacionTramitacionType {
    VocabularyName.validate(name);
  }

  /** A type as it is first read from the source: the database assigns its id on insert. */
  public LicitacionTramitacionType(String name) {
    this(null, name);
  }

  /**
   * Identity, not contents: two instances are the same type when they carry the same assigned
   * {@link LicitacionTramitacionTypeId}, and one the database has not identified is equal only to
   * itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof LicitacionTramitacionType type && id != null && id.equals(type.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
