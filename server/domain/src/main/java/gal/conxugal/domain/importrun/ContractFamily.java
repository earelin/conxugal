package gal.conxugal.domain.importrun;

/**
 * Which family of contracts a covered Órgano's row is about. An Órgano's contratos menores and its
 * licitacións are loaded separately and fare separately, so one run covering both holds one row of
 * each.
 *
 * <p>Deliberately not {@link Importer}. That enum answers <em>what was triggered</em>, and two of
 * its values — the catalogue import and a trigger asking for both families — are nonsense in a
 * coverage row. A column whose type admits values it can never hold is a column every reader has
 * to be told about.
 */
public enum ContractFamily {
  CONTRATOS_MENORES,
  LICITACIONS
}
