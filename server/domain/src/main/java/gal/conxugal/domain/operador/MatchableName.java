package gal.conxugal.domain.operador;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A party's published name reduced to the one form two names are compared on — and used for
 * <strong>nothing but the comparison</strong>. Every name is stored and displayed exactly as it
 * was published; nothing reduced here is ever written anywhere.
 *
 * <p><strong>What it folds</strong>: letter case, accents, punctuation and every kind of spacing.
 * {@code Xestión Ambiental de Contratas, S.L.} and {@code XESTION AMBIENTAL DE CONTRATAS SL} are
 * one name here and two names everywhere else. The fold is total rather than a list of characters
 * to remove: the value is lower-cased, decomposed, and what is not an unaccented ASCII letter or
 * digit is dropped — so a combining accent, a comma, a full stop, a non-breaking space and a
 * double space all disappear by the same rule instead of by an enumeration that the next unseen
 * character escapes.
 *
 * <p><strong>A name that folds to nothing matches nothing.</strong> A cell of punctuation, or one
 * written in a script this fold keeps no character of, reduces to the empty key — and two such
 * names are not evidence that they name the same party, so {@link #of} answers nothing rather than
 * a key that would match every other one like it.
 *
 * <p><strong>The catalogue applies this same fold in SQL</strong>, in
 * {@code JdbcOperadorRepository}, so that a match key computed here finds the operadores holding
 * that name. The two definitions have to agree, which is why this one is expressed as
 * <em>lower-case, NFD, keep {@code [a-z0-9]}</em> — three steps PostgreSQL performs identically
 * with {@code lower}, {@code normalize(…, NFD)} and one {@code regexp_replace}, rather than as a
 * table of accented characters that only one of the two would ever learn a new entry.
 *
 * <p>It is a reduction for <em>matching</em> and never an identity: two firms really can publish
 * one name, which is why every caller here requires the match to be unique before it acts on one.
 */
public record MatchableName(String value) {

  public MatchableName {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isEmpty()) {
      throw new IllegalArgumentException("a matchable name must fold to at least one character");
    }
  }

  /**
   * The key {@code publishedName} is compared on, or nothing at all where the name is absent or
   * folds to nothing that could be compared.
   */
  public static Optional<MatchableName> of(@Nullable String publishedName) {
    if (publishedName == null) {
      return Optional.empty();
    }
    String decomposed =
        Normalizer.normalize(publishedName.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    StringBuilder key = new StringBuilder(decomposed.length());
    for (int index = 0; index < decomposed.length(); index++) {
      char character = decomposed.charAt(index);
      if (isKept(character)) {
        key.append(character);
      }
    }
    return key.isEmpty() ? Optional.empty() : Optional.of(new MatchableName(key.toString()));
  }

  private static boolean isKept(char character) {
    return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
  }

  @Override
  public String toString() {
    return value;
  }
}
