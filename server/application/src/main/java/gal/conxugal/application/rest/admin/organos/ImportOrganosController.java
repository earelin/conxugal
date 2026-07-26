package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;

@Controller("/api/admin/organos")
@Secured("ADMIN")
class ImportOrganosController {

  private final ImportOrganos importOrganos;

  ImportOrganosController(ImportOrganos importOrganos) {
    this.importOrganos = importOrganos;
  }

  @Post("/import")
  ImportOutcomeResponse importOrganos() {
    ImportOutcome outcome = importOrganos.run();
    if (outcome.status() == ImportOutcome.Status.FAILURE) {
      throw new ImportFailedException();
    }
    return ImportOutcomeResponse.of(outcome);
  }
}
