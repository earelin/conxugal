package gal.conxugal.application.rest.organos;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * One Órgano as its own page reads it: its identity, its name, and the contract families it holds
 * visible data for. A second serialisation of the same aggregate rather than an extension of
 * {@link OrganoResponse} — the catalogue row carries {@code active} and {@code termoId} because a
 * caller filters and files by them, and this one carries neither because the page renders neither.
 *
 * <p><b>{@code families} always reaches the wire, empty or not.</b> The serializer's default
 * {@code NON_EMPTY} inclusion would drop an Órgano-holds-nothing answer, and an absent key is a
 * different fact from an empty one: the page renders <em>this Órgano holds no contracts</em> from
 * exactly that emptiness. {@link OrganoResponse} needed the same override for the same reason.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrganoMemberResponse(UUID id, String name, FamiliesResponse families) {

  static OrganoMemberResponse of(OrganoDeContratacion organo, FamiliesResponse families) {
    return new OrganoMemberResponse(
        requireNonNull(organo.id(), "a stored Órgano must carry an id").value(), organo.name(),
        families);
  }
}
