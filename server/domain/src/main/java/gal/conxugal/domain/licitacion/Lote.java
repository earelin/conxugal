package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.money.Money;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One of a procedure's lotes: an award point in its own right, carrying its own award, its own
 * formalisation and its own bidders. {@code id} is a system-assigned identity, {@code null} only
 * until the database assigns it; {@code identifier} is the text the source prints in a lote column
 * and the natural key a re-import matches on within its procedure.
 *
 * <p><strong>The identifier is text, not a number, and that is measured rather than
 * defensive.</strong> {@code OU0028}, {@code LU4001}, {@code LU4031} and {@code CO0642} were all
 * observed in award-table lote cells over 240 procedures. An integer component would reject a real
 * procedure, so nothing here parses or orders it — it is held and matched on, and nothing else.
 * The identifier is held as published, stripped of surrounding whitespace and reduced no further,
 * so a lote published as {@code 05} is held as {@code 05}. Reduction for comparison is
 * {@link LoteKey}'s, and is used for nothing but the comparison.
 *
 * <p><strong>Both extras are optional because the source frequently publishes neither.</strong> On
 * procedure 822054 the {@code Relación de lotes} table was empty — header row only — while
 * {@code Nº lotes} said {@code 2} and the award table named both lotes. So a lote's
 * <em>existence</em> comes from the award table and the lotes table supplies decoration; a model
 * requiring a description or an estimated value would have lost both lotes, and both awards with
 * them. A description that carried only whitespace is held as null rather than as an empty string,
 * because it published nothing.
 *
 * <p>{@code withdrawn} is the marker R13's retention needs: a lote the source no longer publishes
 * for its procedure is kept and marked rather than erased. Nothing here writes it — the
 * reconciliation that does is a later task's.
 */
@MappedEntity("licitacion_lote")
public record Lote(
    @Id @GeneratedValue @Nullable LoteId id,
    LicitacionId licitacionId,
    @MappedProperty("lote_identifier") String identifier,
    @Nullable String description,
    @Nullable Money estimatedValue,
    boolean withdrawn) {

  public Lote {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    identifier = PublishedKey.canonical(identifier, "identifier");
    description = nullIfBlank(description);
  }

  /**
   * A lote as it is first read from the source: the database assigns its id on insert, and nothing
   * an import does withdraws it.
   */
  public Lote(
      LicitacionId licitacionId,
      String identifier,
      @Nullable String description,
      @Nullable Money estimatedValue) {
    this(null, licitacionId, identifier, description, estimatedValue, false);
  }

  /**
   * Identity, not contents: two instances are the same lote when they carry the same assigned
   * {@link LoteId}. The record's own equality would compare the description and the estimated
   * value too, so a lote whose decoration the source filled in between two readings would compare
   * unequal to itself — and it is the same lote, holding the same awards.
   *
   * <p>A lote the database has not assigned an identity to is equal only to itself. Its published
   * identifier names it within its procedure, but two instances holding one are two readings of
   * the same published lote, and deciding they are interchangeable is the store's job at upsert.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Lote lote && id != null && id.equals(lote.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }

  private static @Nullable String nullIfBlank(@Nullable String value) {
    return value == null || Whitespace.isBlank(value) ? null : value;
  }
}
