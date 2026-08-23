package gal.conxugal.domain.licitacion;

/**
 * The order a listing page is asked for. It is a parameter rather than a fixed choice because the
 * two import modes want different ones — but it ships with the single value an initial walk needs.
 *
 * <p>The source resolves an order by column <em>name</em>, so asking for one costs the whole
 * DataTables payload on every request. There is no page without an explicit order.
 */
public enum LicitacionListingOrder {

  /**
   * By the source's own publication identifier, ascending — the only order measured to be stable
   * under concurrent publication. A procedure published while a multi-hour walk runs takes a
   * higher identifier and appends at the end, so pages already read do not shift beneath it.
   *
   * <p>Last-modified descending is the incremental import's order, and the source contract settles
   * that it works. It is not declared here because nothing asks for it yet.
   */
  ID_ASCENDING
}
