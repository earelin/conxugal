package gal.conxugal.domain.importrun;

import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * One run as a reader sees it, or nothing when no run has that identity.
 *
 * <p>Thin over the port on purpose, and worth its own type all the same: the repository is also
 * where a run is claimed, advanced and settled, and a reader holding it holds the guard's write
 * side along with the read it came for.
 */
@Singleton
public class ReadImportRun {

  private final ImportRunRepository importRuns;

  public ReadImportRun(ImportRunRepository importRuns) {
    this.importRuns = importRuns;
  }

  /** The run and every Órgano it covers, with its state already read for abandonment. */
  public Optional<ImportRunReport> read(ImportRunId runId) {
    return importRuns.findRun(runId);
  }
}
