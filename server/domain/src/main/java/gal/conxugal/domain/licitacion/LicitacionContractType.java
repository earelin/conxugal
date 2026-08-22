package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

/**
 * A kind of contract a procedure can be for — <em>Obras</em>, <em>Servizos</em> — as the record's
 * {@code Tipo de contrato} publishes it. {@code id} is a system-assigned identity, {@code null}
 * only until the database assigns it.
 *
 * <p><strong>The published name is the source's identifier here</strong>, and the natural key a
 * re-import matches on. Unlike a state, this vocabulary is published as a bare label: the
 * record's {@code <dd>} carries no code and the listing endpoint carries no type field at all, so
 * there is nothing else to match on. Were a numeric identifier ever measured, it would become a
 * component beside the name and the match would move to it.
 *
 * <p><strong>The name is held stripped of surrounding whitespace, and reduced no further</strong>
 * — no case folding, no collapsing of internal spacing — so two published spellings are two
 * entries, and a vocabulary that folded them would be asserting an equivalence the source never
 * published. Stripping is what ordinary published text on the aggregate does not get, and it is
 * because this one is the natural key: a padded name the adapter forgot to strip matches the entry
 * already stored instead of keying a second one beside it. See {@link PublishedKey} for the rule
 * the three type vocabularies and the publication identifier share.
 *
 * <p>A name that is empty once stripped is refused: an entry keyed on nothing is not a fact about
 * anything.
 *
 * <p>A procedure whose record published no contract type refers to none at all, which is where
 * that absence belongs — never a blank entry standing in for it.
 */
@MappedEntity("licitacion_contract_type")
public record LicitacionContractType(
    @Id @GeneratedValue @Nullable LicitacionContractTypeId id, String name) {

  public LicitacionContractType {
    name = PublishedKey.canonical(name, "name");
  }

  /** A type as it is first read from the source: the database assigns its id on insert. */
  public LicitacionContractType(String name) {
    this(null, name);
  }

  /**
   * Identity, not contents: two instances are the same type when they carry the same assigned
   * {@link LicitacionContractTypeId}. A type the database has not assigned an identity to is equal
   * only to itself — deciding that two readings of one published name are interchangeable is the
   * store's job at upsert, not this record's.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof LicitacionContractType type && id != null && id.equals(type.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
