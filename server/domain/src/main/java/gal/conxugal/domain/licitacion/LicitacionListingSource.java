package gal.conxugal.domain.licitacion;

/**
 * Port for retrieving an Órgano's published licitacións listing. Implemented by an adapter in the
 * {@code infrastructure} module.
 *
 * <p>One page per call, because that is the shape the source offers: an Órgano, an offset and an
 * order. The walk belongs to the use case — this port neither iterates nor remembers where it got
 * to.
 *
 * <p><strong>This is the cheap half of the retrieval.</strong> One call here answers up to a
 * hundred procedures where the record retrieval answers one, and they are two ports rather than
 * one because they are two mechanisms — a single port would hide from its caller that one call
 * costs a thousandth of the other.
 *
 * <p>Answers the page's entries, or throws {@link LicitacionListingUnavailableException} — never an
 * empty success standing in for a failure. An Órgano that has genuinely published nothing is an
 * ordinary answer, and it still carries {@code recordsTotal}.
 */
public interface LicitacionListingSource {

  /**
   * One page of the licitacións an Órgano has published, in the order asked for.
   *
   * <p>The whole published history is reachable this way: the listing takes no date window, and a
   * date passed to it is silently ignored rather than refused.
   *
   * @param sourceKey the Órgano's key as the catalogue stores it
   * @param order the order the source is asked to return the page in
   * @param offset how many entries to skip, zero-based
   * @param pageSize how many entries to ask for
   * @throws LicitacionListingUnavailableException if the source is unreachable or its response
   *     cannot be read
   * @throws IllegalArgumentException if the page asks for more than the source allows
   */
  LicitacionListingPage fetchPage(
      String sourceKey, LicitacionListingOrder order, int offset, int pageSize);
}
