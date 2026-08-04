package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.MarkOrganoForImport;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.UnmarkOrganoForImport;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import java.util.UUID;

/**
 * Opting one Órgano into having its contratos menores imported, and back out. Like the
 * classification writes these hang off the Órgano's own path, so the {@code ADMIN} gate is
 * declared here rather than left to a rule shaped around {@code /api/admin/organos}, which would
 * miss both operations.
 *
 * <p>Marking currently only writes the mark. Once there is an importer, the {@code PUT} also
 * requests a run and its response grows to say whether one started.
 */
@Controller("/api/admin/organo")
@Secured("ADMIN")
class OrganoImportMarkController {

  private final MarkOrganoForImport markOrganoForImport;
  private final UnmarkOrganoForImport unmarkOrganoForImport;

  OrganoImportMarkController(MarkOrganoForImport markOrganoForImport,
      UnmarkOrganoForImport unmarkOrganoForImport) {
    this.markOrganoForImport = markOrganoForImport;
    this.unmarkOrganoForImport = unmarkOrganoForImport;
  }

  @Put("/{id}/importable")
  @Status(HttpStatus.NO_CONTENT)
  void mark(@PathVariable UUID id) {
    markOrganoForImport.mark(new OrganoId(id));
  }

  @Delete("/{id}/importable")
  @Status(HttpStatus.NO_CONTENT)
  void unmark(@PathVariable UUID id) {
    unmarkOrganoForImport.unmark(new OrganoId(id));
  }
}
