package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

/**
 * An entry of the Nomenclature of Territorial Units for Statistics — the regulated list the
 * European Union publishes for naming the region a contract is performed in. Structurally
 * identical to {@link Cpv} and separate from it because the two map two tables and one mapped
 * entity cannot map both; see that record for the reasoning in full, which holds here unchanged —
 * matched on the code and never on the description, unseeded because the list is versioned rather
 * than closed, and held once because thousands of procedures cite the same entry.
 *
 * <p>The one difference is what a mismatch would cost. A CPV names what is bought and a NUTS names
 * where, so a row filed under the wrong vocabulary is not a visible error but a procedure
 * classified by a region as though it were a purpose. That is why the two carry separate
 * identifier types rather than sharing one.
 */
@MappedEntity("nut")
public record Nut(
    @Id @GeneratedValue @Nullable NutId id, String code, @Nullable String description) {

  public Nut {
    code = PublishedKey.canonical(code, "code");
    description = PublishedText.orNullWhenBlank(description);
  }

  /** An entry as it is first met on a record: the database assigns its id on insert. */
  public Nut(String code) {
    this(null, code, null);
  }

  /**
   * Identity, not contents, on {@link Cpv#equals}' reasoning: two instances are the same entry
   * when they carry the same assigned {@link NutId}, and one the database has not assigned an
   * identity to is equal only to itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Nut nut && id != null && id.equals(nut.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
