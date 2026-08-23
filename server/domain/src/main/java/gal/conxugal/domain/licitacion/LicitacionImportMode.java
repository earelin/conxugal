package gal.conxugal.domain.licitacion;

/**
 * How an Órgano's licitacións are to be imported, decided from how far its history has already
 * been loaded.
 *
 * <p>The rule is {@link #of(LicitacionImportStatus)} and it lives here alone so that every trigger
 * reaching an Órgano agrees about it — a mode chosen from the trigger that arrived rather than from
 * the Órgano's own state is how a half-loaded Órgano ends up restarted or skipped.
 *
 * <p>Four lines duplicated from the other contract family's rule rather than one rule parameterised
 * by family, on the same reasoning as the second table: two small switches that can be read
 * independently beat one that has to be read twice, and neither family's progress can then be
 * decided by the other's.
 *
 * <p>{@link #INCREMENTAL} is returned here and implemented nowhere. An Órgano that reaches it is
 * skipped, with that as the recorded reason, until the feature that walks the listing by
 * last-modified date exists.
 */
public enum LicitacionImportMode {
  INITIAL,
  RESUMED,
  INCREMENTAL;

  /**
   * The mode an Órgano at this status takes. Exhaustive over the status, so a fourth one cannot be
   * added without deciding here what it imports.
   */
  public static LicitacionImportMode of(LicitacionImportStatus status) {
    return switch (status) {
      case NEVER_STARTED -> INITIAL;
      case INCOMPLETE -> RESUMED;
      case COMPLETE -> INCREMENTAL;
    };
  }
}
