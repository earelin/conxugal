package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.AssignOrganoToTermo;
import gal.conxugal.domain.organo.ClearOrganoTermo;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import jakarta.validation.Valid;
import java.util.UUID;

/**
 * Filing one Órgano under a term and taking it out again. These hang off the Órgano's own
 * path rather than a taxonomía one because they change the Órgano, not the tree — which is
 * also why the {@code ADMIN} gate is declared here rather than left to a rule shaped around
 * the taxonomía paths, where it would miss both operations.
 */
@Controller("/api/admin/organo")
@Secured("ADMIN")
class OrganoClassificationController {

  private final AssignOrganoToTermo assignOrganoToTermo;
  private final ClearOrganoTermo clearOrganoTermo;

  OrganoClassificationController(AssignOrganoToTermo assignOrganoToTermo,
      ClearOrganoTermo clearOrganoTermo) {
    this.assignOrganoToTermo = assignOrganoToTermo;
    this.clearOrganoTermo = clearOrganoTermo;
  }

  @Put("/{id}/termo")
  @Status(HttpStatus.NO_CONTENT)
  void assign(@PathVariable UUID id, @Valid @Body AssignTermoRequest request) {
    assignOrganoToTermo.assign(new OrganoId(id), new TermoId(request.termoId()));
  }

  @Delete("/{id}/termo")
  @Status(HttpStatus.NO_CONTENT)
  void clear(@PathVariable UUID id) {
    clearOrganoTermo.clear(new OrganoId(id));
  }
}
