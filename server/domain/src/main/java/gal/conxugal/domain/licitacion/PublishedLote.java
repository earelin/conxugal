package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import org.jspecify.annotations.Nullable;

/**
 * One of a procedure's lotes as the source published it, before it becomes a {@link Lote}.
 *
 * <p><strong>A lote's existence comes from the tables that name it, not from the lotes
 * table.</strong> On 822054 {@code Relación de lotes} was header-row-only while {@code Nº lotes}
 * said {@code 2} and the award table named both; a parse that discovered lotes from the lotes
 * table alone would have found none and lost both awards with them. So the lote columns of the
 * record's tables establish which lotes there are, and {@code Relación de lotes} supplies the
 * description and the estimated value — decoration that is frequently absent, which is why both
 * are nullable.
 *
 * <p><strong>The identifier is held as the source spelled it.</strong> A lote published as
 * {@code 05} is held as {@code 05}, matching {@link Lote#identifier}; {@link LoteKey}'s reduction
 * is for comparison and never for storage, and is what lets a row spelling the same lote
 * {@code 5} find it. The identifier is text rather than a number because it is not always one —
 * {@code OU0028}, {@code LU4001} and {@code CO0642} were all observed in award-table lote cells.
 *
 * @param identifier the lote as the source printed it, stripped at its ends and reduced no
 *     further
 * @param description the {@code Descrición} the lotes table published, where it published one
 * @param estimatedValue the {@code Valor estimado} the lotes table published, where it published
 *     one
 */
public record PublishedLote(
    String identifier, @Nullable String description, @Nullable Money estimatedValue) {

  /**
   * A lote naming the procedure as a whole is a contradiction, so it is refused rather than held.
   * That is what lets every caller reduce this identifier to a key without an absent branch to
   * handle.
   */
  public PublishedLote {
    identifier = PublishedKey.canonical(identifier, "identifier");
    if (LoteKey.normalise(identifier).isEmpty()) {
      throw new IllegalArgumentException(
          "identifier must name a lote rather than the procedure as a whole");
    }
    description = PublishedText.orNullWhenBlank(description);
  }

  /**
   * The form this lote is matched on, which is {@link LoteKey}'s reduction of its published
   * identifier. Present by construction: the constructor refuses an identifier that reduces to
   * the procedure as a whole.
   */
  public String key() {
    return LoteKey.normalise(identifier).orElseThrow();
  }
}
