package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.FiscalIdentifier;
import org.jspecify.annotations.Nullable;

/**
 * One firm named inside a {@link PublishedBidder.Consortium}'s nested list, which publishes each
 * member's own identifier beside its own name — {@code A70319678 - PRACE SERVICIOS Y OBRAS SA}.
 *
 * <p><strong>All 80 measured member entries carried an ordinary identifier</strong>, which is what
 * makes the members the reliable half of a consortium row and the consortium itself the doubtful
 * one. A member whose entry does not split into an identifier and a name yields the whole entry as
 * a name and no identifier, on {@code ContratistaCell}'s failure direction: a member that resolves
 * to nobody costs one membership, a member resolved to the wrong operador corrupts the catalogue.
 *
 * <p>Nothing here resolves or catalogues anything. Whether a member becomes an operador, and
 * whether the membership between it and the consortium is recorded, is a later task's.
 *
 * @param name the member's name, as published
 * @param fiscalIdentifier the member's own identifier, or null where the entry carried none
 */
public record PublishedConsortiumMember(
    @Nullable String name, @Nullable FiscalIdentifier fiscalIdentifier) {

  public PublishedConsortiumMember {
    name = PublishedText.orNullWhenBlank(name);
  }
}
