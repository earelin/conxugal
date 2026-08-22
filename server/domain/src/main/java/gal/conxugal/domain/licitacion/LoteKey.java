package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one form a lote cell is reduced to before anything is compared against it. One definition
 * because four of the record's tables carry a lote column — the award, the formalisation, the NUT
 * classification and the bidder list — and they do not spell a lote the same way.
 *
 * <p><strong>Measured over 240 procedures, not guessed.</strong> The award, formalisation and NUT
 * tables write {@code _} for a row standing for the procedure as a whole while the bidder table
 * writes {@code -}, so a parse hard-coding either loses the other. Zero-padding varies
 * <em>within</em> a table rather than between them — the award table produced both {@code 1} and
 * {@code 05} in one sample. And a lote identifier is not always a number: {@code OU0028},
 * {@code LU4001}, {@code LU4031} and {@code CO0642} were all observed, so what remains after
 * reduction is compared as text and never as an integer.
 *
 * <p><strong>What believing the raw cell costs</strong>: joining an award row's bidder count
 * against the bidder rows for that lote agrees on 63 of 158 rows raw, and on all 158 normalised.
 * Every one of those 95 disagreements is an artefact — {@code _} against {@code -}, or {@code 05}
 * against {@code 5} — and a mismatched count is what sends a procedure to the outstanding ledger,
 * so the unnormalised join would fail most procedures the source publishes perfectly well.
 *
 * <p><strong>This normalises for comparison and never for storage.</strong> A lote is stored under
 * the identifier the source published, so a lote published as {@code 05} is held as {@code 05};
 * this is what lets a row spelling it {@code 5} find that lote all the same.
 */
public final class LoteKey {

  private static final String PROCEDURE_WIDE_UNDERSCORE = "_";
  private static final String PROCEDURE_WIDE_DASH = "-";

  private LoteKey() {}

  /**
   * The lote {@code publishedCell} names, or nothing at all where it names the procedure as a
   * whole — which is what {@code _}, {@code -}, the empty string, a blank string and an absent
   * cell all mean. Anything else is stripped of surrounding whitespace and of leading zeros, and
   * what remains is the key.
   *
   * <p>Nothing else is reduced: letter case, internal spacing and every other character make a
   * different lote. A cell of nothing but zeros keeps one, so {@code 000} is the lote {@code 0}
   * rather than the procedure as a whole — a published lote never reduces to the absence of one.
   */
  public static Optional<String> normalise(@Nullable String publishedCell) {
    if (publishedCell == null) {
      return Optional.empty();
    }
    String stripped = Whitespace.strip(publishedCell);
    if (stripped.isEmpty()
        || PROCEDURE_WIDE_UNDERSCORE.equals(stripped)
        || PROCEDURE_WIDE_DASH.equals(stripped)) {
      return Optional.empty();
    }
    return Optional.of(withoutLeadingZeros(stripped));
  }

  private static String withoutLeadingZeros(String value) {
    int start = 0;
    while (start < value.length() - 1 && value.charAt(start) == '0') {
      start++;
    }
    return value.substring(start);
  }
}
