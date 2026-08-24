package gal.conxugal.domain.licitacion;

/**
 * Thrown by a {@link LicitacionRecordSource} adapter when a procedure's record cannot be retrieved,
 * or when what came back cannot be read as a record at all, rather than let a half-built procedure
 * reach the store. A procedure stored from a partial parse is stored as authoritative and nothing
 * ever returns to it, so refusing the whole record is the cheaper failure: the walk records the
 * procedure as outstanding and a later run fetches it again.
 *
 * <p>A record the source published thinly is not this. The per-procedure page is answered from an
 * identifier space shared with contratos menores, and several of its labelled fields are genuinely
 * absent on real pages — those are ordinary answers carrying nothing, not failures.
 */
public class LicitacionRecordUnavailableException extends RuntimeException {

  public LicitacionRecordUnavailableException(String message) {
    super(message);
  }

  public LicitacionRecordUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
