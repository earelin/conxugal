package gal.conxugal.domain.importrun;

/**
 * Which import produced a run. Recorded so an outcome can say what ran, not so the guard can tell
 * them apart: at most one import runs at a time across the whole system, so a live run of either
 * refuses a claim of either.
 */
public enum Importer {
  ORGANOS,
  CONTRATOS_MENORES
}
