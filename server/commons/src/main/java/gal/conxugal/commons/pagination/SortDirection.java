package gal.conxugal.commons.pagination;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Which way a sorted read runs. Shared rather than declared per list, because a reader meets
 * several paginated lists in one session and one of them spelling its direction differently from
 * the rest is a defect they would experience as inconsistency. Named for the sort rather than
 * called {@code Direction} alone, because a caller that parses one usually has a pagination
 * library's own direction type in scope beside it.
 *
 * <p>{@link #parse} accepts exactly {@code asc} and {@code desc}, the two spellings every list
 * publishes. {@code ascending}, {@code descending}, {@code ASC} and an empty value all answer
 * nothing at all — <strong>a direction that fell back to ascending when it was not recognised
 * would serve one ordering while claiming another</strong>, which nothing downstream could detect,
 * and which for a hand-written statement is how an unchecked property name reaches an
 * {@code ORDER BY}. Answering nothing rather than throwing is what lets each caller decide how a
 * refusal is reported; <strong>none of them may decide it is ascending.</strong>
 */
public enum SortDirection {
  ASC,
  DESC;

  public static Optional<SortDirection> parse(@Nullable String published) {
    return switch (published) {
      case "asc" -> Optional.of(ASC);
      case "desc" -> Optional.of(DESC);
      case null, default -> Optional.empty();
    };
  }
}
