package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a trigger into a run: which Órganos it covers, the order they are walked in, what one
 * failing means to the rest, and the verdict the whole thing is settled with.
 *
 * <p><strong>Asking and doing are two calls</strong>, because a trigger has to answer in
 * milliseconds about work measured in days. {@link #claimAll()} and {@link #claimOrgano} decide
 * eligibility, take the guard and write the run with its coverage enumerated — short, and the only
 * part whose answer a caller waits for. {@link #execute} takes that identity and does the walking,
 * blocking for as long as it takes. Submitting it somewhere it can block is the trigger's business,
 * not this class's.
 *
 * <p><strong>Eligibility is decided here and nowhere else.</strong> Every trigger — a mark, an
 * administrator's button, a future scheduler — asks the same question of the same code, so none of
 * them can disagree with the others about which Órganos an import covers.
 *
 * <p><strong>Órganos are walked one at a time</strong>, each finished before the next begins. The
 * reason is reportability rather than pacing: it gives the per-Órgano outcomes a well-defined
 * order, so at any moment a run is working on one identifiable Órgano.
 *
 * <p><strong>A failing Órgano does not take the run down with it.</strong> The run moves to the
 * next; what it and the Órganos before it stored stands. A run of four hundred Órganos that gave
 * up at the fortieth because one source answered badly would be worth far less than the
 * thirty-nine imports it already had. Isolating it is {@link ImportCoveredOrgano}'s, which is
 * also where what one Órgano's turn amounts to is decided — this class only counts what came
 * back.
 *
 * <p><strong>The verdict is read off the failed rows, not the successful ones.</strong> Nothing
 * failed is a success, whatever else happened: a run whose Órganos were every one skipped, or every
 * one stopped, imported nothing and is still not a failure. Reading it the other way round —
 * nothing succeeded, therefore failed — would report the ordinary state of a fully loaded
 * catalogue as a fault every night.
 *
 * <p><strong>Nothing here checks whether an Órgano went inactive mid-run.</strong> Only the
 * catalogue import deactivates one, and the guard forbids it running while this does, so the
 * obligation is met by the guard rather than by a check.
 */
@Singleton
public class ImportContratosMenores {

  private static final Logger LOG = LoggerFactory.getLogger(ImportContratosMenores.class);

  private final OrganoRepository organos;
  private final ImportRunRepository importRuns;
  private final ImportCoveredOrgano coveredOrgano;

  public ImportContratosMenores(
      OrganoRepository organos,
      ImportRunRepository importRuns,
      ImportCoveredOrgano coveredOrgano) {
    this.organos = organos;
    this.importRuns = importRuns;
    this.coveredOrgano = coveredOrgano;
  }

  /**
   * Claims a run over every eligible Órgano. One covering none is still a run: the catalogue
   * having nothing marked is an ordinary answer, not a refusal, and it settles as a success that
   * imported nothing.
   */
  public ContratosMenoresImportClaim claimAll() {
    return claimCovering(
        organos.findAllByActiveTrueAndImportableTrue().stream()
            .map(ImportContratosMenores::identityOf)
            .toList());
  }

  /**
   * Claims a run over one named Órgano.
   *
   * <p>Eligibility is settled before the guard is touched, so an Órgano that cannot be imported
   * starts no run and leaves no trace of having asked — and the refusal it answers is the one that
   * says so, rather than whichever refusal the guard would have given.
   *
   * @throws OrganoNotFoundException if no Órgano has this identity
   */
  public ContratosMenoresImportClaim claimOrgano(OrganoId organoId) {
    OrganoDeContratacion organo =
        organos.findById(organoId).orElseThrow(() -> new OrganoNotFoundException(organoId));
    if (!organo.eligibleForImport()) {
      return ContratosMenoresImportClaim.notEligible();
    }
    return claimCovering(List.of(organoId));
  }

  private ContratosMenoresImportClaim claimCovering(List<OrganoId> covered) {
    return importRuns
        .claim(Importer.CONTRATOS_MENORES, covered)
        .map(ContratosMenoresImportClaim::claimed)
        .orElseGet(ContratosMenoresImportClaim::alreadyRunning);
  }

  /**
   * Walks every Órgano the run covers and settles it. Blocks until the last of them is done, which
   * for an initial import of a large catalogue is days.
   *
   * <p>Settled from a finally, so nothing thrown out of the walking — an Error as much as an
   * exception — can leave the guard held by a run this process has already given up on, which would
   * refuse every import in the system until the abandonment bound passed.
   *
   * <p><strong>Nothing is written to a run this process does not hold the guard for.</strong> It is
   * asked once here, before anything is read, and again by every walk before every batch; a run
   * that has gone quiet past the abandonment bound has already been claimed by whoever triggered
   * next, and its record is theirs. Writing anyway would settle a run this process abandoned as
   * though it had finished the work the live run is still doing — and, asked to execute a run that
   * is already over, would overwrite the verdict and the failed Órganos it named.
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

  private static OrganoId identityOf(OrganoDeContratacion organo) {
    return Objects.requireNonNull(organo.id(), "a stored Órgano always carries its identity");
  }
}
