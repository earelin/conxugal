package gal.conxugal.domain.licitacion;

/**
 * How far an Órgano's licitacións history has been loaded.
 *
 * <p>Three values, not two. A never-loaded Órgano and a half-loaded one are both "not complete",
 * and they take different import modes; collapsing them would let an Órgano whose initial import
 * was interrupted be treated as up to date, which is the defect the three-state fact exists to
 * prevent.
 *
 * <p>{@link #NEVER_STARTED} is never stored: a state row is created when an Órgano's import first
 * starts, so an Órgano with no row <em>is</em> never started. That is what makes every Órgano
 * already marked for the other contract family take the initial mode on the first licitacións run
 * covering it, with no migration and no re-marking.
 *
 * <p>Its own type rather than the other family's, held in its own table: neither family's progress
 * is ever read as the other's, and there is no shared value to get wrong.
 */
public enum LicitacionImportStatus {
  NEVER_STARTED,
  INCOMPLETE,
  COMPLETE
}
