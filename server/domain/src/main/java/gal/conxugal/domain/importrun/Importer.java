package gal.conxugal.domain.importrun;

/**
 * Which import produced a run. Recorded so an outcome can say what ran, not so the guard can tell
 * them apart: at most one import runs at a time across the whole system, so a live run of either
 * refuses a claim of either.
 *
 * <p>This is <em>what was triggered</em>, never a summary of what the run turned out to cover.
 * {@link #AMBAS_FAMILIAS} is a trigger that asked for both contract families, which is a different
 * fact from a run that happened to cover two — and it is named so rather than {@code CONTRATOS},
 * which reads as a superset of {@link #CONTRATOS_MENORES} by name alone.
 */
public enum Importer {
  ORGANOS,
  CONTRATOS_MENORES,
  LICITACIONS,
  AMBAS_FAMILIAS
}
