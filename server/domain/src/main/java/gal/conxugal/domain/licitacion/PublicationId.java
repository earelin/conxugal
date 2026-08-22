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
 * <p>A blank or untrimmed value is refused rather than repaired — see {@link PublishedKey} for
 * the rule this shares with the type vocabularies, which are keyed on published text too.
 * {@code toString} is the bare identifier because messages interpolate it directly.
 */
@TypeDef(type = DataType.STRING, converter = PublicationIdConverter.class)
public record PublicationId(String value) {

  public PublicationId {
    PublishedKey.validate(value, "publicationId");
  }

  @Override
  public String toString() {
    return value;
  }
}
