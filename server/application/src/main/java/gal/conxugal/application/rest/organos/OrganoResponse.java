package gal.conxugal.application.rest.organos;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * One Órgano of the catalogue. A null {@code termoId} is the unclassified case: there is no
 * separate collection for it, so callers filter this list rather than asking for one — which
 * is why the field is sent as an explicit null instead of being dropped under the
 * serializer's default {@code NON_EMPTY} inclusion.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrganoResponse(UUID id, String name, boolean active, @Nullable UUID termoId) {

  static OrganoResponse of(OrganoDeContratacion organo) {
    return new OrganoResponse(
        requireNonNull(organo.id(), "a stored Órgano must carry an id").value(), organo.name(),
        organo.active(), idOf(organo.termoId()));
  }

  private static @Nullable UUID idOf(@Nullable TermoId termoId) {
    return termoId == null ? null : termoId.value();
  }
}
