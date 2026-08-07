package gal.conxugal.domain.organo;

/**
 * How far an Órgano's contratos menores history has been loaded.
 *
 * <p>Three values, not two. A never-loaded Órgano and a half-loaded one are both "not complete",
 * and they take different import modes; collapsing them would let an Órgano whose initial import
 * was interrupted be treated as up to date, which is the defect the three-state fact exists to
 * prevent.
 *
 * <p>{@link #NEVER_STARTED} is never stored: a state row is created when an Órgano's import first
 * starts, so an Órgano with no row <em>is</em> never started. It is a value here rather than an
 * absent row at the caller so that reading a state always answers with a status.
 */
public enum ContratosMenoresImportStatus {
  NEVER_STARTED,
  INCOMPLETE,
  COMPLETE
}
