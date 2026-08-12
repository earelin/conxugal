package gal.conxugal.domain.importrun;

/**
 * Thrown when an import is asked for while another already holds the guard.
 *
 * <p>It names no run and no importer, deliberately: the guard admits one import at a time across
 * the whole system, so what refused this one may be a different Órgano's, the catalogue's, or the
 * same trigger arriving twice. All the caller can be told — and all it needs — is that an import is
 * running and this one did not start.
 *
 * <p>Distinct from an Órgano being ineligible, which is a refusal that will repeat until the
 * catalogue or the mark changes; this one is a matter of timing, and asking again later works.
 */
public class ImportAlreadyRunningException extends RuntimeException {

  public ImportAlreadyRunningException() {
    super("An import is already running, so no further import was started");
  }
}
