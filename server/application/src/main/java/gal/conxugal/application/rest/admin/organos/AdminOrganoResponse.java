package gal.conxugal.application.rest.admin.organos;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.contrato.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * One Órgano of the catalogue as an administrator sees it: everything the shared
 * {@code OrganoResponse} carries, plus the contratos menores import mark and how far that import
 * has got. It is a second serialisation of the same aggregate rather than extra fields on the
 * first because both are {@code ADMIN} facts, and the shared read is available to any
 * authenticated caller.
 *
 * <p>The mark and the state are independent: the mark is what an administrator asked for, the
 * state is how far the system got. An Órgano is serialised with both so the administration UI can
 * tell a half-loaded Órgano from a complete one rather than inferring it is up to date.
 *
 * <p>The null {@code termoId} is sent explicitly, for the reason it is there too: unclassified is
 * a value callers filter on, not a missing field.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminOrganoResponse(
    UUID id,
    String name,
    boolean active,
    @Nullable UUID termoId,
    boolean importable,
    ContratosMenoresImportStatus importState) {

  static AdminOrganoResponse of(
      OrganoDeContratacion organo, ContratosMenoresImportStatus importState) {
    return new AdminOrganoResponse(
        requireNonNull(organo.id(), "a stored Órgano must carry an id").value(), organo.name(),
        organo.active(), idOf(organo.termoId()), organo.importable(), importState);
  }

  private static @Nullable UUID idOf(@Nullable TermoId termoId) {
    return termoId == null ? null : termoId.value();
  }
}
