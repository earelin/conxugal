package gal.conxugal.application.rest.paging;

import static gal.conxugal.application.rest.request.Refusals.refused;

import io.micronaut.data.model.Sort;

/**
 * The {@code sort} a paginated read takes, split into the two halves the contract spells it as:
 * {@code property,direction}. The third of the parameters
 * {@link PagingParameters} covers, and shared for the same reason — the spelling is one rule
 * across every list in this API, and an operation inventing a second one is a difference a reader
 * meets as inconsistency.
 *
 * <p><b>It validates the shape and the direction, and deliberately not the property.</b> Which
 * properties an operation offers is the operation's own closed set — two for a contratos menores
 * browse, others elsewhere — and that set is what the ordering's safety rests on: a native
 * statement appends a property name to its {@code ORDER BY} verbatim, so the name must be matched
 * against a vocabulary this system owns rather than merely being well-formed. A shared type that
 * accepted any property would look like validation while providing none, so the property comes
 * back as text for the operation to parse with its own enum.
 *
 * <p><b>Both halves are refused rather than degraded.</b> Micronaut's own binder takes any property
 * name and quietly turns an unrecognised direction into ascending, which would answer a different
 * ordering under the label the caller asked for — and the envelope states no ordering back, so
 * nothing on the wire would reveal it.
 */
public record SortParameter(String property, Sort.Order.Direction direction) {

  private static final String ASCENDING = "asc";
  private static final String DESCENDING = "desc";

  /**
   * The parameter split at its one comma, or a refusal. Exactly one comma: a value naming no
   * direction and a value naming two are both spellings the contract does not offer.
   */
  public static SortParameter of(String sort) {
    int comma = sort.indexOf(',');
    if (comma < 0 || sort.indexOf(',', comma + 1) >= 0) {
      throw refused("sort must be property,direction");
    }
    return new SortParameter(sort.substring(0, comma), directionOf(sort.substring(comma + 1)));
  }

  private static Sort.Order.Direction directionOf(String published) {
    return switch (published) {
      case ASCENDING -> Sort.Order.Direction.ASC;
      case DESCENDING -> Sort.Order.Direction.DESC;
      default -> throw refused("sort direction must be asc or desc");
    };
  }
}
