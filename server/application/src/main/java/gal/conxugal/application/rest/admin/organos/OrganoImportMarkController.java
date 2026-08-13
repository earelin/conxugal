package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.organo.MarkOrganoForImport;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
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
 * <p>Marking asks for an import of <strong>that Órgano alone</strong>, never a sweep of every
 * marked one, and it asks after the mark is written.
 *
 * <p><strong>This is the one endpoint that catches the refusals the triggers let out.</strong>
 * Their handlers are registered by exception type and so answer everywhere, and a refusal reaching
 * one from here would answer {@code 409} — telling the caller the mark did not apply, when the
 * whole point is that it did. A mark landing while an import runs is refused rather than queued
 * and the mark itself stands, so the refusal is part of a {@code 200} body instead.
 *
 * <p>Unmarking is unchanged. It stops a run for that Órgano at the next batch boundary rather than
 * synchronously, so there is nothing for its response to report.
 */
@Controller("/api/admin/organo")
@Secured("ADMIN")
class OrganoImportMarkController {

  private final MarkOrganoForImport markOrganoForImport;
  private final UnmarkOrganoForImport unmarkOrganoForImport;
  private final StartContratosMenoresImport startImport;

  OrganoImportMarkController(MarkOrganoForImport markOrganoForImport,
      UnmarkOrganoForImport unmarkOrganoForImport, StartContratosMenoresImport startImport) {
    this.markOrganoForImport = markOrganoForImport;
    this.unmarkOrganoForImport = unmarkOrganoForImport;
    this.startImport = startImport;
  }

  @Put("/{id}/importable")
  MarkOutcomeResponse mark(@PathVariable UUID id) {
    OrganoId organoId = new OrganoId(id);
    markOrganoForImport.mark(organoId);
    return importOf(organoId);
  }

  @Delete("/{id}/importable")
  @Status(HttpStatus.NO_CONTENT)
  void unmark(@PathVariable UUID id) {
    unmarkOrganoForImport.unmark(new OrganoId(id));
  }

  /**
   * The import the mark asked for, as a run or as the reason there is none. An unknown Órgano
   * never reaches here: the mark refuses it first, and a 404 is the honest answer when nothing was
   * written at all.
   */
  private MarkOutcomeResponse importOf(OrganoId organoId) {
    try {
      return MarkOutcomeResponse.started(startImport.startOrgano(organoId).value());
    } catch (ImportAlreadyRunningException _) {
      return MarkOutcomeResponse.refused(ImportRefusal.IMPORT_ALREADY_RUNNING);
    } catch (OrganoNotEligibleForImportException _) {
      return MarkOutcomeResponse.refused(ImportRefusal.ORGANO_NOT_ELIGIBLE);
    }
  }
}
