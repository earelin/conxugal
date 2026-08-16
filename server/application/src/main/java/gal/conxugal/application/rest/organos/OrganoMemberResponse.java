package gal.conxugal.application.rest.organos;

import static java.util.Objects.requireNonNull;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * One Órgano as its own page reads it: its identity, its name, and the contract families it holds
 * visible data for. A second serialisation of the same aggregate rather than an extension of
 * {@link OrganoResponse} — the catalogue row carries {@code active} and {@code termoId} because a
 * caller filters and files by them, and this one carries neither because the page renders neither.
 *
 * <p><b>{@code families} always reaches the wire, empty or not</b> — an absent key is a different
 * fact from an empty one, since the page renders <em>this Órgano holds no contracts</em> from
 * exactly that emptiness. It takes no {@code @JsonInclude} override to hold: the serializer's
 * default {@code NON_EMPTY} inclusion asks a property's own serializer whether it is empty, and
 * the one for a bean-typed property answers that only for a null. A {@link FamiliesResponse} is
 * always built, so there is nothing to drop. {@link OrganoResponse} does carry the override, but
 * for a reason that does not apply here — its {@code termoId} is an actual null.
 *
 * <p>Were {@code families} ever a {@code Map}, that would stop being true and the override would
 * be needed: a map serializer reports an empty map as empty. What guards the shape either way is
 * the round trip over HTTP that asserts the serialised key, not an annotation.
 */
@Serdeable
public record OrganoMemberResponse(UUID id, String name, FamiliesResponse families) {

  static OrganoMemberResponse of(OrganoDeContratacion organo, FamiliesResponse families) {
    return new OrganoMemberResponse(
        requireNonNull(organo.id(), "a stored Órgano must carry an id").value(), organo.name(),
        families);
  }
}
