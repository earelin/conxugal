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

  /**
   * Reduces whatever it is handed, so an identifier read back from the store and rebuilt is the
   * same identifier, and a caller that has not stripped its input cannot produce one that fails
   * to match. Refuses a value that is empty once surrounding whitespace is ignored: there is no
   * such identifier, and {@link #of} is how that case is asked about rather than thrown at.
   */
  public FiscalIdentifier {
    Objects.requireNonNull(value, "value must not be null");
    value = Whitespace.strip(value).toUpperCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("value must not be empty once trimmed");
    }
  }

  /**
   * The identifier a contract published, or nothing at all when it is unusable — absent, or empty
   * once surrounding whitespace is ignored. An award whose identifier is unusable yields no
   * operador rather than an invented one, and returning nothing is what makes that branch
   * impossible for a caller to overlook.
   *
   * <p>Nothing beyond emptiness disqualifies an identifier: the source publishes irregular but
   * genuine ones — foreign VAT numbers, malformed NIFs — and rejecting them would discard real
   * awards.
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
    return Optional.of(new FiscalIdentifier(published));
  }

  @Override
  public String toString() {
    return value;
  }
}
