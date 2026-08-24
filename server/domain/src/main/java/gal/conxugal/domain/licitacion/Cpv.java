package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

/**
 * An entry of the Common Procurement Vocabulary — the regulated list the European Union publishes
 * for classifying what a contract is for. {@code id} is a system-assigned identity, {@code null}
 * only until the database assigns it; {@code code} is the identifier the list itself assigns, and
 * the natural key an import matches on.
 *
 * <p><strong>A row of its own because the list is regulated and shared.</strong> A CPV code is not
 * this source's coinage and not this procedure's property: it is an external standard that
 * thousands of procedures cite, so it is held once and referred to, rather than copied into every
 * classification row. That is what lets a reader be offered exactly the codes a selection
 * contains, and what makes narrowing by one a reference rather than a text match against a column
 * repeated a million times.
 *
 * <p><strong>An import matches on the code and never on the description.</strong> The code is what
 * the regulated list identifies an entry by; the description is wording about it, which is
 * translated, revised, and repeated across sibling entries. A store unique on the description
 * would reject a real entry, and one matching on it would merge entries the list distinguishes or
 * create a second row for wording that changed. So the description carries no constraint, exactly
 * as {@link LicitacionState}'s label carries none, and the code is what the system is unique on.
 *
 * <p><strong>The description is nullable because the source does not always supply one.</strong>
 * An earlier reading of this record said the CPV table publishes the code alone; measured on
 * 822054, it does not — the cell carries the code, a non-breaking space, then Spanish wording
 * ({@code 45000000} then {@code Trabajos de construcción}), and an import splits the two and
 * stores both. Null means nobody has supplied one — never that the entry has none.
 *
 * <p><strong>Nothing seeds this table and nothing validates against it.</strong> The list being
 * regulated is not the same as it being closed: CPV is versioned — the 2008 revision retired codes
 * the 2003 one issued — and this system imports procedures published across both. A seeded
 * catalogue would turn a code retired before our copy was taken into a foreign-key violation and a
 * rejected procedure, which is the harm storing values as published exists to prevent. An unseen
 * code costs a row.
 *
 * <p>The code is held stripped of surrounding whitespace and reduced no further — no case folding,
 * no reformatting of the separator a code may carry — so two published spellings stay two entries
 * rather than one asserting an equivalence the list never stated. A code that is empty once
 * stripped is refused: an entry keyed on nothing is not a fact about anything.
 */
@MappedEntity("cpv")
public record Cpv(
    @Id @GeneratedValue @Nullable CpvId id, String code, @Nullable String description) {

  public Cpv {
    code = PublishedKey.canonical(code, "code");
    description = PublishedText.orNullWhenBlank(description);
  }

  /** An entry as it is first met on a record: the database assigns its id on insert. */
  public Cpv(String code) {
    this(null, code, null);
  }

  /**
   * Identity, not contents: two instances are the same entry when they carry the same assigned
   * {@link CpvId}. The record's own equality would compare the description too, so one entry read
   * either side of its wording being supplied would compare unequal to itself.
   *
   * <p>An entry the database has not assigned an identity to is equal only to itself. Its code
   * identifies it in the regulated list, but two instances holding one are two readings of the
   * same entry, and deciding they are interchangeable is the store's job at upsert.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Cpv cpv && id != null && id.equals(cpv.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
