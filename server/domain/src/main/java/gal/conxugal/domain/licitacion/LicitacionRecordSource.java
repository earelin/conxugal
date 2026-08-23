package gal.conxugal.domain.licitacion;

/**
 * Port for retrieving one published procedure whole. Implemented by an adapter in the
 * {@code infrastructure} module.
 *
 * <p><strong>This is the expensive half of the retrieval.</strong> One call here answers one
 * procedure where the listing port answers up to a hundred, and it is a separate port for that
 * reason: a single port would hide from its caller that one call costs a thousandth of the other,
 * which is the property the walk's cost, its cursor and its resumption are all designed around.
 *
 * <p>It answers a {@link LicitacionRecord} — what the source published — rather than a
 * {@link Licitacion}. The aggregate is the store's to build, and a port that answered one would put
 * the model's shape inside the adapter.
 */
public interface LicitacionRecordSource {

  /**
   * The procedure the source publishes under {@code publicationId}, or a failure.
   *
   * <p>Never a half-built procedure: a record whose labels cannot be found, or a response that is
   * not a record at all, raises. A procedure stored from a partial parse is stored as authoritative
   * and nothing ever returns to it.
   *
   * @param publicationId the procedure's identifier, as the listing published it
   * @throws LicitacionRecordUnavailableException if the source is unreachable, or its response
   *     cannot be read as a record
   */
  LicitacionRecord fetchRecord(PublicationId publicationId);
}
