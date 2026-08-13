package gal.conxugal.application.rest.admin.importruns;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ReadImportRun;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.security.annotation.Secured;
import java.util.UUID;

/**
 * What one import run amounted to. The triggers answer before the work starts, so this is where
 * their outcome is read — and for a multi-day import it is the only place an administrator can
 * find out anything at all about a run they asked for.
 *
 * <p><strong>The verdict arrives already derived.</strong> A run whose process died still says in
 * progress on disk, and the repository's read is what turns it into abandoned on the way out.
 * Nothing here re-derives it: a second reader applying that rule could disagree with the guard
 * about whether an import may start, which is the one question the rule exists to settle.
 */
@Controller(ImportRunLocation.PATH)
@Secured("ADMIN")
class ImportRunController {

  private final ReadImportRun readImportRun;

  ImportRunController(ReadImportRun readImportRun) {
    this.readImportRun = readImportRun;
  }

  @Get("/{id}")
  ImportRunResponse run(@PathVariable UUID id) {
    ImportRunId runId = new ImportRunId(id);
    return readImportRun
        .read(runId)
        .map(ImportRunResponse::of)
        .orElseThrow(() -> new ImportRunNotFoundException(runId));
  }
}
