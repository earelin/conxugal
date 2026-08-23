package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import org.jspecify.annotations.Nullable;

/**
 * The one form ordinary published text is held in: exactly as it was handed over, or nothing at
 * all where it published nothing. One definition because the award point's records hold nine such
 * values between them and a copy of the rule that drifted would let one of them store an empty
 * string where its neighbours store null — which reads as <em>published and empty</em> rather than
 * as <em>not published</em>, and is a different fact.
 *
 * <p><b>It is the opposite half of {@link PublishedKey}, and the difference is the point.</b> A
 * value a row is keyed on strips its padding and refuses to be blank, because a padded key would
 * file a second row beside the one already stored. Ordinary text does neither: it is stored
 * byte-for-byte as published, down to its surrounding whitespace, because the adapter has already
 * trimmed what the source's markup added and anything left is the source's own. Whichever of the
 * two a component gets is a decision about that component, and holding them apart is what keeps
 * the decision visible.
 */
final class PublishedText {

  private PublishedText() {}

  /**
   * {@code value} unchanged, or null where it carries nothing — absent already, or blank once
   * every character that would not survive stripping is ignored.
   *
   * <p>Null rather than an empty string, because the two would otherwise both be reachable for one
   * published absence and nothing downstream could tell which it was looking at.
   */
  static @Nullable String orNullWhenBlank(@Nullable String value) {
    return value == null || Whitespace.isBlank(value) ? null : value;
  }
}
