package gal.conxugal.domain.licitacion;

/**
 * Port for the published contract-type vocabulary. Implemented by the {@code infrastructure}
 * module.
 *
 * <p>Its own port rather than one shared with the other two type vocabularies, so that a
 * procedure type cannot be handed to it: the three are structurally identical and would otherwise
 * be interchangeable at every call site, which is exactly what the separate identifier types
 * exist to prevent.
 *
 * <p>Like the state's, this vocabulary is not seeded and not validated against — an unseen name
 * simply creates its row — and there is no delete: nothing an import does removes a published
 * value.
 */
public interface LicitacionContractTypeRepository {

  /**
   * Stores the type, matching it against what is held <strong>by its name</strong> — which is the
   * source's identifier for this vocabulary, since the record publishes no code for it — and
   * answering the stored type, whose identity a procedure then refers to. Re-storing one the
   * source has published before changes nothing.
   *
   * <p>The only method here, and the absence of a finder beside it is deliberate: the upsert
   * answers the stored type, so nothing storing a procedure needs to look one up first, and a
   * read nobody makes is a read nobody has specified.
   */
  LicitacionContractType upsert(LicitacionContractType type);
}
