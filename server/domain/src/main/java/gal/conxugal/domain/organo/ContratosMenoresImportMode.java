package gal.conxugal.domain.organo;

/**
 * How an Órgano's contratos menores are to be imported, decided from how far its history has
 * already been loaded.
 *
 * <p>The rule is {@link #of(ContratosMenoresImportStatus)} and it lives here alone so that the
 * manual trigger, the mark trigger and the future scheduler cannot disagree about it — a mode
 * chosen from the trigger that arrived rather than from the Órgano's own state is how a
 * half-loaded Órgano ends up restarted or skipped.
 *
 * <p>A historical re-read is deliberately not a value here. No trigger selects one automatically,
 * so it cannot be the answer to <em>what should happen to this Órgano now</em>.
 */
public enum ContratosMenoresImportMode {
  INITIAL,
  RESUMED,
  INCREMENTAL;

  /**
   * The mode an Órgano at this status takes. Exhaustive over the status, so a fourth one cannot be
   * added without deciding here what it imports.
   */
  public static ContratosMenoresImportMode of(ContratosMenoresImportStatus status) {
    return switch (status) {
      case NEVER_STARTED -> INITIAL;
      case INCOMPLETE -> RESUMED;
      case COMPLETE -> INCREMENTAL;
    };
  }
}
