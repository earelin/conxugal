package gal.conxugal.domain.licitacion;

/**
 * Which branch an upsert took: the store did not hold the procedure before, or it did and
 * refreshed it in place.
 *
 * <p>Named rather than a boolean, because the two branches are what a run's outcome counts and a
 * caller reading {@code false} would have to know which of them it stood for. Only the write can
 * tell them apart — once the row is stored, nothing afterwards can say whether it was new.
 */
public enum UpsertOperation {

  /** The store held no procedure under this publication identifier, and now holds one. */
  ADDED,

  /** The store already held the procedure, and its published values were written over. */
  REFRESHED
}
