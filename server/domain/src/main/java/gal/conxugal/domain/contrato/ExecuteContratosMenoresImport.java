package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.organo.OrganoId;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks the Órganos a claimed run covers and settles it with what they came to.
 *
 * <p>Long and blocking, and separate from the claim for that reason: this runs for days, while the
 * decision to start it was over in milliseconds. It takes the identity that decision produced —
 * typed rather than a bare {@code UUID}, which is what stops an Órgano's id reaching it — and looks
 * up everything else from the run itself. Submitting it somewhere it can block is the trigger's
 * business, not this class's.
 *
 * <p><strong>Órganos are walked one at a time</strong>, each finished before the next begins. The
 * reason is reportability rather than pacing: it gives the per-Órgano outcomes a well-defined
 * order, so at any moment a run is working on one identifiable Órgano.
 *
 * <p><strong>A failing Órgano does not take the run down with it.</strong> The run moves to the
 * next; what it and the Órganos before it stored stands. A run of four hundred Órganos that gave up
 * at the fortieth because one source answered badly would be worth far less than the thirty-nine
 * imports it already had. Isolating it is {@link ImportCoveredOrgano}'s, which is also where what
 * one Órgano's turn amounts to is decided — this class only counts what came back.
 *
 * <p><strong>The verdict is read off the failed rows, not the successful ones.</strong> Nothing
 * failed is a success, whatever else happened: a run whose Órganos were every one skipped, or every
 * one stopped, imported nothing and is still not a failure. Reading it the other way round —
 * nothing succeeded, therefore failed — would report the ordinary state of a fully loaded catalogue
 * as a fault every night.
 *
 * <p><strong>Nothing here checks whether an Órgano went inactive mid-run.</strong> Only the
 * catalogue import deactivates one, and the guard forbids it running while this does, so the
 * obligation is met by the guard rather than by a check.
 */
@Singleton
public class ExecuteContratosMenoresImport {

  private static final Logger LOG = LoggerFactory.getLogger(ExecuteContratosMenoresImport.class);

  private final ImportRunRepository importRuns;
  private final ImportCoveredOrgano coveredOrgano;

  public ExecuteContratosMenoresImport(
      ImportRunRepository importRuns, ImportCoveredOrgano coveredOrgano) {
    this.importRuns = importRuns;
    this.coveredOrgano = coveredOrgano;
  }

  /**
   * Walks every Órgano the run covers and settles it. Blocks until the last of them is done, which
   * for an initial import of a large catalogue is days.
   *
   * <p>Settled from a finally, so nothing thrown out of the walking — an Error as much as an
   * exception — can leave the guard held by a run this process has already given up on, which would
   * refuse every import in the system until the abandonment bound passed.
   *
   * <p><strong>The verdict is never written to a run this process does not hold the guard
   * for.</strong> It is asked here before anything is read, by every walk before every batch, and
   * once more before settling; a run that has gone quiet past the abandonment bound has already
   * been claimed by whoever triggered next, and its record is theirs. Writing anyway would settle a
   * run this process abandoned as though it had finished the work the live run is still doing —
   * and, asked to execute a run that is already over, would overwrite the verdict and the failed
   * Órganos it named.
   *
   * <p>The per-Órgano rows are best-effort by comparison: an Órgano that reads nothing at all —
   * unmarked before its turn, gone from the catalogue — is settled without asking the guard again,
   * because those take no measurable time and asking would double the reads settling them makes.
   * One that walks or refreshes needs no ask here either, having put the question to the guard
   * twice per page from inside its own loop. A stall long enough to lose the guard between two of
   * them leaves a row written against a run nobody reads any more; the verdict is what stops that
   * being visible.
   */
  public void execute(ImportRunId runId) {
    if (!importRuns.holdsGuard(runId)) {
      LOG.warn(
          "Contratos menores run {} is not the live import; it walks nothing and settles nothing",
          runId);
      return;
    }
    ImportRunState verdict = ImportRunState.FAILED;
    boolean stillTheLiveRun = true;
    try {
      Walked walked = walkCoveredOrganos(runId);
      stillTheLiveRun = walked.stillTheLiveRun();
      verdict = verdictOver(walked.covered(), walked.failed());
    } finally {
      if (stillTheLiveRun) {
        settle(runId, verdict);
      }
    }
  }

  /**
   * What walking a run's coverage came to. {@code stillTheLiveRun} is false when the guard went
   * part-way: the walking stops there and the run's record is left exactly as this process found
   * it, which is what an abandoned run is supposed to look like.
   */
  private record Walked(int covered, int failed, boolean stillTheLiveRun) {}

  private Walked walkCoveredOrganos(ImportRunId runId) {
    List<OrganoId> covered = coveredOrganosOf(runId);
    int failed = 0;
    for (OrganoId organoId : covered) {
      Optional<ImportRunOrganoState> settled = coveredOrgano.run(runId, organoId);
      if (settled.isEmpty()) {
        LOG.warn(
            "Contratos menores run {} stopped holding the import guard while walking Órgano {};"
                + " what it covered is another import's now, and it settles nothing",
            runId, organoId);
        return new Walked(covered.size(), failed, false);
      }
      if (settled.get() == ImportRunOrganoState.FAILED) {
        failed++;
      }
    }
    return new Walked(covered.size(), failed, true);
  }

  /**
   * The Órganos to walk, read from the run rather than looked up again. The list was fixed when the
   * run was claimed, and a sweep taking days would otherwise silently change shape underneath
   * itself every time an administrator marked something.
   */
  private List<OrganoId> coveredOrganosOf(ImportRunId runId) {
    return importRuns
        .findRun(runId)
        .map(
            report ->
                report.coveredOrganos().stream().map(ImportRunOrganoCoverage::organoId).toList())
        .orElseGet(
            () -> {
              LOG.warn(
                  "Contratos menores run {} was asked to execute but is not recorded; it covers"
                      + " nothing this process can walk",
                  runId);
              return List.of();
            });
  }

  /**
   * Read over what failed. Partial success is the likeliest verdict of a multi-Órgano run, not an
   * edge case — carrying on past a failing Órgano is precisely what produces it.
   */
  private static ImportRunState verdictOver(int covered, int failed) {
    if (failed == 0) {
      return ImportRunState.SUCCEEDED;
    }
    return failed == covered ? ImportRunState.FAILED : ImportRunState.PARTIALLY_SUCCEEDED;
  }

  /**
   * Settles the run with zero counts: every contract was already counted against its own Órgano as
   * its batch committed, and passing totals here would add them a second time.
   */
  private void settle(ImportRunId runId, ImportRunState verdict) {
    if (!importRuns.holdsGuard(runId)) {
      LOG.warn(
          "Contratos menores run {} stopped being the live import before it could be settled as"
              + " {}; its record is another import's now and it is left as it stands",
          runId, verdict);
      return;
    }
    try {
      importRuns.complete(runId, verdict, 0, 0);
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores import finished as {} but its run {} was not settled; the contracts"
              + " are as this import left them, and the guard stays held until the abandonment"
              + " bound passes",
          verdict, runId, e);
    }
  }
}
