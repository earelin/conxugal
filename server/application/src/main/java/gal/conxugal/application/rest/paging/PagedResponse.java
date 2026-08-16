package gal.conxugal.application.rest.paging;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.data.model.Page;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.function.Function;

/**
 * One page of a collection, as every paginated read of this API answers it. Declared once, and
 * generic, because three specifications' lists take this shape and a reader meeting several of
 * them in one session would experience a second envelope as a defect rather than as a design.
 *
 * <p><b>It is 1-based, and the framework's page is not.</b> The number here is the number a URL
 * carries and a paging control shows, so no layer above this one has to remember which side of the
 * boundary it is holding. {@link #of} is the only place the conversion happens.
 *
 * <p><b>Both totals travel because a reader is owed both.</b> The page span is what a control
 * renders; the entry count is an answer in its own right — <em>how many contracts this Órgano
 * awarded in this year</em> — rather than an intermediate value for computing the other. Neither is
 * derived by a client, and because both are read off one {@link Page} they cannot disagree.
 *
 * <p><b>{@code items}, not {@code content}.</b> The envelope names what the domain has rather than
 * what the persistence library calls its field, and it publishes none of that library's other
 * vocabulary — no pageable, no mode, no always-empty sort produced by an internal serialiser.
 *
 * <p><b>An empty page still carries an empty {@code items}.</b> The serializer's default inclusion
 * drops an empty collection, which would take the key out of exactly the response that most needs
 * it — a page past the last one, where the contract declares {@code items} required and a client
 * reading it has no way to tell an empty page from a field the server left off.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

  public PagedResponse {
    items = List.copyOf(items);
  }

  /**
   * The page as it arrived from the store, in the shape the contract publishes, with each entry
   * put through {@code row}.
   *
   * <p><b>The span is taken rather than divided again.</b> {@link Page#getTotalPages()} already
   * derives it from the same count this envelope carries, so recomputing it here would be a second
   * expression of one number and somewhere for the two to disagree — at the last page, which is
   * where nobody looks.
   */
  public static <S, T> PagedResponse<T> of(Page<S> page, Function<S, T> row) {
    return new PagedResponse<>(
        page.getContent()
            .stream()
            .map(row)
            .toList(),
        page.getPageNumber() + 1,
        page.getSize(),
        page.getTotalSize(),
        page.getTotalPages());
  }
}
