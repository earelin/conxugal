package gal.conxugal.domain.operador;

import gal.conxugal.commons.text.Whitespace;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one reduction the system performs on a published value: surrounding whitespace removed and
 * letters upper-cased, and nothing else. Two awards name the same operador when their published
 * identifiers reduce to the same value, so {@code b12345678}, {@code " B12345678 "} and
 * {@code B12345678} are one operador holding {@code B12345678}.
 *
 * <p>What comes out is <b>the identifier the system holds</b> — what the catalogue is unique on,
 * what a lookup compares against, and what is displayed. There is no second value beside it, so
 * no reader can pick the wrong one; the price is that the published letter case is retained
 * nowhere.
 *
 * <p><b>Everything else survives.</b> Internal spacing, punctuation and any differing character
 * make a different identifier and therefore a different operador. Merging two real suppliers is
 * as damaging as splitting one, and either way the cross-Órgano aggregation this exists for
 * fails silently.
 *
 * <p><b>This reduction is for identifiers and never for names.</b> A name is displayed exactly as
 * published, down to its case and internal spacing, and folding one here would erase the
 * distinction the two rules rest on — which is why this is not a general-purpose text helper and
 * takes no argument that is not a fiscal identifier.
 */
public final class FiscalIdentifier {

  private FiscalIdentifier() {}

  /**
   * The canonical form of a published identifier, or nothing at all when the identifier is
   * unusable — absent, or empty once surrounding whitespace is ignored. An award whose identifier
   * is unusable yields no operador rather than an invented one, and returning nothing is what
   * makes that branch impossible for a caller to overlook.
   *
   * <p>Nothing beyond emptiness disqualifies an identifier: the source publishes irregular but
   * genuine ones — foreign VAT numbers, malformed NIFs — and rejecting them would discard real
   * awards.
   *
   * <p>The result is already canonical, so canonicalising it again returns it unchanged and a
   * value re-read from the store is the same value.
   *
   * <p>Stripping here duplicates what the source adapter already does, deliberately. The same
   * reduction has to govern what a <em>user</em> types when looking an operador up, and a
   * function that is correct only when its caller has already stripped is one that silently
   * mismatches the day a caller has not.
   */
  public static Optional<String> canonical(@Nullable String published) {
    if (published == null) {
      return Optional.empty();
    }
    String canonical = Whitespace.strip(published).toUpperCase(Locale.ROOT);
    return canonical.isEmpty() ? Optional.empty() : Optional.of(canonical);
  }
}
