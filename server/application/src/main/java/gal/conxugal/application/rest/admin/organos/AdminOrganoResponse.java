package gal.conxugal.application.rest.admin.organos;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * One Órgano of the catalogue as an administrator sees it: everything the shared
 * {@code OrganoResponse} carries, plus the contratos menores import mark. It is a second
 * serialisation of the same aggregate rather than an extra field on the first because the mark is
 * an {@code ADMIN} fact, and the shared read is available to any authenticated caller.
 *
 * <p>The null {@code termoId} is sent explicitly, for the reason it is there too: unclassified is
 * a value callers filter on, not a missing field.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminOrganoResponse(
    UUID id, String name, boolean active, @Nullable UUID termoId, boolean importable) {

  static AdminOrganoResponse of(OrganoDeContratacion organo) {
    return new AdminOrganoResponse(
        requireNonNull(organo.id(), "a stored Órgano must carry an id").value(), organo.name(),
        organo.active(), idOf(organo.termoId()), organo.importable());
  }

  private static @Nullable UUID idOf(@Nullable TermoId termoId) {
    return termoId == null ? null : termoId.value();
  }
}
