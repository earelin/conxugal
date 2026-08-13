package gal.conxugal.application.rest.admin.contratosmenores;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.application.rest.admin.importruns.ImportRunLocation;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import java.util.UUID;

/**
 * Asking for a contratos menores import: every marked Órgano, or one named Órgano. Two scopes of
 * one operation, so they are one controller and the answer is built in one place — the identity a
 * trigger returns and the address it points at have to agree, and two classes agreeing is
 * something to maintain rather than something that holds.
 *
 * <p><strong>Neither trigger waits for the import.</strong> An initial import runs for days, so
 * what a trigger can answer with is which run is doing the work and where to read it, never how it
 * went. Pairing the claim with the walk and choosing the thread the walk runs on are
 * {@link StartContratosMenoresImport}'s; this asks for an import and reports what it was told.
 *
 * <p><strong>Refusals are let out.</strong> Both — the guard being held and the named Órgano not
 * being eligible — reach a handler that answers {@code 409} with a problem type of its own, and
 * neither is caught here: nothing was written and the request genuinely did not happen, which is
 * the opposite of what the import mark means by the same refusals.
 */
@Controller("/api/admin")
@Secured("ADMIN")
class ContratosMenoresImportController {

  private final StartContratosMenoresImport startImport;

  ContratosMenoresImportController(StartContratosMenoresImport startImport) {
    this.startImport = startImport;
  }

  @Post("/contratos-menores/import")
  HttpResponse<ImportRunStartedResponse> importAll() {
    return accepted(startImport.startAll());
  }

  @Post("/organo/{id}/contratos-menores/import")
  HttpResponse<ImportRunStartedResponse> importOrgano(@PathVariable UUID id) {
    return accepted(startImport.startOrgano(new OrganoId(id)));
  }

  /**
   * The run, as its identity and as the address that reports it. The wrapper stops here: the body
   * carries the bare UUID, and a {@code {"value": …}} in one means it leaked.
   */
  private static HttpResponse<ImportRunStartedResponse> accepted(ImportRunId runId) {
    return HttpResponse
        .accepted(ImportRunLocation.of(runId))
        .body(new ImportRunStartedResponse(runId.value()));
  }
}
