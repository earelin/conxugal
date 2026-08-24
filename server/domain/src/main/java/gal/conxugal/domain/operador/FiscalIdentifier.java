package gal.conxugal.domain.operador;

import gal.conxugal.commons.text.Whitespace;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The fiscal identifier an operador is catalogued under, in the one form the system holds it:
 * surrounding whitespace removed and letters upper-cased, and nothing else. Two awards name the
 * same operador when their published identifiers reduce to the same value, so {@code b12345678},
 * {@code " B12345678 "} and {@code B12345678} are one operador holding {@code B12345678}.
 *
 * <p><b>The canonical form is what this type is.</b> Every way of building one reduces, so an
 * instance cannot hold a published spelling and there is no second value beside it — the
 * catalogue is unique on it, a lookup compares against it, and it is what is displayed. The price
 * is that the published letter case is retained nowhere.
 *
 * <p><b>Everything else survives.</b> Internal spacing, punctuation and any differing character
 * make a different identifier and therefore a different operador. Merging two real suppliers is
 * as damaging as splitting one, and either way the cross-Órgano aggregation this exists for fails
 * silently.
 *
 * <p><b>This reduction is for identifiers and never for names.</b> A name is displayed exactly as
 * published, down to its case and internal spacing, and folding one here would erase the
 * distinction the two rules rest on — which is why nothing that is not a fiscal identifier can be
 * passed through this type.
 *
 * <p>{@code toString} is the bare identifier because messages interpolate it directly.
 */
@TypeDef(type = DataType.STRING, converter = FiscalIdentifierConverter.class)
public record FiscalIdentifier(String value) {

  private static final String DASH_PLACEHOLDER = "-";
  private static final String TEMPORARY_PLACEHOLDER_PREFIX = "TEMP-";

  /**
   * Reduces whatever it is handed, so an identifier read back from the store and rebuilt is the
   * same identifier, and a caller that has not stripped its input cannot produce one that fails
   * to match. Refuses a value that is empty once surrounding whitespace is ignored: there is no
   * such identifier, and {@link #of} is how that case is asked about rather than thrown at.
   *
   * <p><b>It refuses nothing else, and a published placeholder least of all.</b> Reading a row
   * back rebuilds one through here, so refusing a value the store already holds would turn a data
   * condition into a failed read; {@link #of} is where a placeholder is turned away, before it can
   * become a row.
   */
  public FiscalIdentifier {
    Objects.requireNonNull(value, "value must not be null");
    value = Whitespace.strip(value).toUpperCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("value must not be empty once trimmed");
    }
  }

  /**
   * The identifier a contract published, or nothing at all when it is unusable — absent, empty
   * once surrounding whitespace is ignored, or a published placeholder. An award whose identifier
   * is unusable yields no operador rather than an invented one, and returning nothing is what
   * makes that branch impossible for a caller to overlook.
   *
   * <p><b>A published placeholder is what the source emits in place of an identifier it does not
   * have.</b> Two forms are known and named, so the test is decidable rather than a matter of
   * judgement: a lone dash, and the {@code TEMP-…} form, allocated per publication and therefore
   * identifying nothing beyond it. Admitting either would catalogue one operador holding it,
   * pooling unrelated parties under whichever name was published last — the shared identity this
   * branch exists to refuse.
   *
   * <p>Nothing beyond emptiness and those two forms disqualifies an identifier: the source
   * publishes irregular but genuine ones — foreign VAT numbers, malformed NIFs, values merely
   * carrying a dash — and rejecting them would discard real awards.
   *
   * <p><b>This is the gate for published input, and the only one.</b> The canonical constructor
   * stays permissive because the other way a value reaches this type is by being read back out of
   * the store, and a placeholder already persisted has to rehydrate rather than fail the read.
   *
   * <p>Stripping here duplicates what the source adapter already does, deliberately. The same
   * reduction has to govern what a <em>user</em> types when looking an operador up, and a rule
   * that holds only when its caller has already stripped is one that silently mismatches the day
   * a caller has not.
   */
  public static Optional<FiscalIdentifier> of(@Nullable String published) {
    if (published == null || Whitespace.isBlank(published)) {
      return Optional.empty();
    }
    FiscalIdentifier identifier = new FiscalIdentifier(published);
    if (identifier.isPublishedPlaceholder()) {
      return Optional.empty();
    }
    return Optional.of(identifier);
  }

  /** Judged on the canonical form, so a padded or lower-cased placeholder is still one. */
  private boolean isPublishedPlaceholder() {
    return DASH_PLACEHOLDER.equals(value) || value.startsWith(TEMPORARY_PLACEHOLDER_PREFIX);
  }

  @Override
  public String toString() {
    return value;
  }
}
