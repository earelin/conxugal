package gal.conxugal.application.rest.admin.importruns;

import gal.conxugal.domain.importrun.ImportRunId;

/** Thrown when the run read is asked for an identity no run has. */
class ImportRunNotFoundException extends RuntimeException {

  private final ImportRunId runId;

  ImportRunNotFoundException(ImportRunId runId) {
    super("No import run exists with id %s".formatted(runId));
    this.runId = runId;
  }

  ImportRunId getRunId() {
    return runId;
  }
}
