package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.OptionalLong;

/**
 * The identifier the source published a procedure under — the natural key a re-import matches on.
 * Its own type so it cannot be passed where a {@link LicitacionId} is expected: the two are both
 * identifiers of the same procedure and mean opposite things, one assigned by this system and one
 * by the source, and a mix-up between them is the defect this feature was drafted wrong once
 * already.
 *
 * <p><strong>It wraps text, though every identifier measured is an integer.</strong> How the
 * source mints them is the source's business and nothing published fixes the shape, so this holds
 * what was published rather than a reading of it — a source that started issuing alphanumeric
 * identifiers would cost a parse at the adapter rather than a column type, a migration and a
 * re-import. Nothing here sums or increments one, and nothing matches on anything but the text.
 *
 * <p>One caller does need it <em>ordered</em>, and it pays that parse rather than comparing the
 * text: the operador name rank breaks a tie on the higher contract identifier against a contrato
 * menor's {@code long}, and text order is not numeric order — {@code "9"} sorts above
 * {@code "10"}. See {@link Licitacion} for why that comparison arises at all.
 *
 * <p><strong>It names a publication, not a licitación.</strong> Measured, the source serves both
 * contract families from one identifier space, so an identifier denotes one publication whichever
 * family it belongs to. The type lives here because this is the only family that holds one as a
 * value; a contrato menor still carries its own as a bare {@code long}.
 *
 * <p><strong>The canonical form is what this type is.</strong> Every way of building one strips
 * surrounding whitespace, so an identifier read back from the store and rebuilt is the same
 * identifier, and a caller that has not stripped its input cannot produce one that fails to match
 * the row already stored. It refuses a value that is empty once stripped: there is no such
 * publication. See {@link PublishedKey} for the rule this shares with the type vocabularies, which
 * are keyed on published text too.
 *
 * <p><strong>Nothing else is reduced.</strong> Internal spacing, punctuation and letter case all
 * make a different identifier and therefore a different publication. A fiscal identifier folds its
 * case because that reduction is measured; nothing says this one is case-insensitive, and folding
 * on a guess would merge two publications the source distinguishes.
 *
 * <p>{@code toString} is the bare identifier because messages interpolate it directly.
 */
@TypeDef(type = DataType.STRING, converter = PublicationIdConverter.class)
public record PublicationId(String value) {

  public PublicationId {
    value = PublishedKey.canonical(value, "publicationId");
  }

  /**
   * This identifier as the number the operador name rank orders on, or nothing at all where the
   * source did not mint a number. Every identifier measured is one — 18 700 to 829 000 — and both
   * families draw from that single publication id space, so a licitación's identifier compares
   * against a contrato menor's {@code long} with no family discriminator between them.
   *
   * <p><strong>The parse is why the column is text and this is a method.</strong> Nothing published
   * fixes the shape of an identifier, so the type holds what was published and pays a parse at the
   * one caller that needs an order — rather than a column type, a migration and a re-import the day
   * the source stops minting integers.
   *
   * <p><strong>Nothing at all, never a rank engineered to lose.</strong> A publication this cannot
   * read a number out of ranks no name: its award still resolves its operador and still stores, and
   * no name advances from it. Ranking such an identifier as text is the alternative that has to be
   * refused rather than merely avoided — {@code "9"} would outrank {@code "10"}, silently
   * corrupting a tie-break the shipped contratos menores family has already populated.
   */
  public OptionalLong asNumber() {
    try {
      return OptionalLong.of(Long.parseLong(value));
    } catch (NumberFormatException notNumeric) {
      return OptionalLong.empty();
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
