package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

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
 * re-import. Nothing sorts, sums or increments one; it is matched on and nothing else.
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

  @Override
  public String toString() {
    return value;
  }
}
