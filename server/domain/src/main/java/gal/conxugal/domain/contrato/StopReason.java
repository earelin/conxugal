package gal.conxugal.domain.contrato;

/**
 * What cut a walk off before it could decide how far the history had been loaded.
 *
 * <p>A reason rather than a flag, because its two values ask opposite things of the caller. An
 * Órgano unmarked mid-walk is an ordinary, deliberate ending and the rest of the run carries on; a
 * run that stopped holding the guard is the run itself being over, and carrying on would have this
 * process writing to a record another import has already claimed.
 *
 * <p>Neither is a property of any one walk: both are answered by {@link
 * ReadContratosMenoresWindow}, which is how every walk reads its windows.
 */
public enum StopReason {
  /** The Órgano stopped being active and marked while the walk was running. */
  UNMARKED,
  /** The run behind the walk stopped being the live one, so it is no longer the walk's to use. */
  GUARD_LOST
}
