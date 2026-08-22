package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

/**
 * A state a procedure can be in, as the source publishes it: a {@code code} and the label printed
 * beside it. {@code id} is a system-assigned identity, {@code null} only until the database
 * assigns it; {@code code} is the source's own identifier — the {@code estado} its listing
 * publishes — and the natural key a re-import matches on.
 *
 * <p><strong>The label repeats, and nothing here stops it.</strong> Codes 101 and 102 are both
 * published as <em>Histórico</em>. So the pair is the fact and the label alone is not: a store
 * unique on the label would reject a real state, and a filter keyed on it would merge two the
 * source distinguishes. Both are held, and the code is what the system is unique on.
 *
 * <p><strong>Nothing validates the code against a known set</strong>, because there is no known
 * set. Ten codes were observed over some two thousand listing rows and code 7 was not among them;
 * higher {@code 1xx} codes may exist. An unseen code is stored as published, which is what keeps
 * it from costing a real procedure — so this is a code and not an enum.
 *
 * <p>The label is nullable, and null means the source published nothing there. A label carrying
 * only whitespace is held as null rather than as an empty string, because it published nothing
 * and null is already this vocabulary's word for that.
 */
@MappedEntity("licitacion_state")
public record LicitacionState(
    @Id @GeneratedValue @Nullable LicitacionStateId id, int code, @Nullable String label) {

  public LicitacionState {
    label = label == null || Whitespace.isBlank(label) ? null : label;
  }

  /** A state as it is first read from the source: the database assigns its id on insert. */
  public LicitacionState(int code, @Nullable String label) {
    this(null, code, label);
  }

  /**
   * Identity, not contents: two instances are the same state when they carry the same assigned
   * {@link LicitacionStateId}. The record's own equality would compare the label too, so the same
   * stored row read twice with a corrected label in between would compare unequal to itself.
   *
   * <p>A state the database has not assigned an identity to is equal only to itself. There is
   * nothing to match it on: {@code code} identifies it at the source, but two values holding one
   * are two readings of the same published state, and deciding they are interchangeable is the
   * store's job at upsert, not this record's.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof LicitacionState state && id != null && id.equals(state.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
