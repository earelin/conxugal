package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.ContratosMenoresImportMode;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
 * <p><strong>A failing Órgano does not take the run down with it.</strong> Its row is settled
 * failed with the reason and the run moves to the next; what it and the Órganos before it stored
 * stands. A run of four hundred Órganos that gave up at the fortieth because one source answered
 * badly would be worth far less than the thirty-nine imports it already had.
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

  /**
   * Recorded on an Órgano whose history is already loaded. It is neither imported nor failed, and
   * reporting it as either would be untrue; naming the mode says why nothing was done.
   */
  private static final String ALREADY_LOADED =
      "Nothing to import: this Órgano's history is loaded, and the %s mode does not exist yet"
          .formatted(ContratosMenoresImportMode.INCREMENTAL);

  private static final String GONE_FROM_THE_CATALOGUE =
      "This Órgano is no longer in the catalogue";

  private static final Logger LOG = LoggerFactory.getLogger(ImportContratosMenores.class);

  private final OrganoRepository organos;
  private final ImportRunRepository importRuns;
  private final ImportOrganoContratosMenores walk;

  public ImportContratosMenores(
      OrganoRepository organos,
      ImportRunRepository importRuns,
      ImportOrganoContratosMenores walk) {
    this.organos = organos;
    this.importRuns = importRuns;
    this.walk = walk;
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
   */
  public void execute(ImportRunId runId) {
    ImportRunState verdict = ImportRunState.FAILED;
    try {
      List<OrganoId> covered = coveredOrganosOf(runId);
      int failed = 0;
      for (OrganoId organoId : covered) {
        if (importOne(runId, organoId) == ImportRunOrganoState.FAILED) {
          failed++;
        }
      }
      verdict = verdictOver(covered.size(), failed);
    } finally {
      settle(runId, verdict);
    }
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

  /** One Órgano, from the mode rule through to its settled row. Answers the state it settled at. */
  private ImportRunOrganoState importOne(ImportRunId runId, OrganoId organoId) {
    Settlement settlement = outcomeFor(runId, organoId);
    settleOrgano(runId, organoId, settlement);
    return settlement.state();
  }

  /**
   * How one Órgano ended, and why if the reason is not the state itself. It exists so the walking
   * and the recording of it stay separate acts — the record is evidence about an import, never a
   * participant in it.
   */
  private record Settlement(ImportRunOrganoState state, @Nullable String reason) {

    static Settlement succeeded() {
      return new Settlement(ImportRunOrganoState.SUCCEEDED, null);
    }

    static Settlement stopped() {
      return new Settlement(ImportRunOrganoState.STOPPED, null);
    }

    static Settlement skipped(String reason) {
      return new Settlement(ImportRunOrganoState.SKIPPED, reason);
    }

    static Settlement failed(String reason) {
      return new Settlement(ImportRunOrganoState.FAILED, reason);
    }
  }

  /**
   * The mode rule decides what happens to an Órgano, not the trigger that arrived — so a mark, a
   * manual sweep and a future scheduler all resume a half-loaded Órgano and all leave a loaded one
   * alone.
   *
   * <p>Eligibility is asked again first, because a sweep reaches its four-hundredth Órgano days
   * after the run enumerated it, and one unmarked in between must have nothing read for it at all.
   */
  private Settlement outcomeFor(ImportRunId runId, OrganoId organoId) {
    Optional<OrganoDeContratacion> found = organos.findById(organoId);
    if (found.isEmpty()) {
      return Settlement.failed(GONE_FROM_THE_CATALOGUE);
    }
    OrganoDeContratacion organo = found.get();
    if (!organo.eligibleForImport()) {
      return Settlement.stopped();
    }
    return switch (ContratosMenoresImportMode.of(organo.importStatus())) {
      case INITIAL, RESUMED -> walked(runId, organo, organoId);
      case INCREMENTAL -> Settlement.skipped(ALREADY_LOADED);
    };
  }

  /**
   * Everything the walk can throw is caught, not only the source being unavailable. The rule the
   * run owes the rest of its Órganos is that one of them failing costs only that one, and a walk
   * has more than its source to fail on. An Error is deliberately not caught: it says this process
   * is no longer to be trusted with the other Órganos either.
   */
  private Settlement walked(ImportRunId runId, OrganoDeContratacion organo, OrganoId organoId) {
    try {
      ContratosMenoresImportSummary summary =
          walk.run(runId, organo, () -> stillEligible(organoId));
      return summary.stopped() ? Settlement.stopped() : Settlement.succeeded();
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores import of Órgano {} failed within run {}; what it stored stands and"
              + " the run carries on to the Órganos after it",
          organoId, runId, e);
      return Settlement.failed(reasonOf(e));
    }
  }

  /** Asked at every batch boundary of the walk, which is what makes an unmark stop it. */
  private boolean stillEligible(OrganoId organoId) {
    return organos.findById(organoId).filter(OrganoDeContratacion::eligibleForImport).isPresent();
  }

  /**
   * Records how one Órgano ended, and never lets that recording cost the run. A settlement write
   * that failed and was allowed out would abandon every Órgano after this one over a row.
   */
  private void settleOrgano(ImportRunId runId, OrganoId organoId, Settlement settlement) {
    try {
      importRuns.finishOrgano(runId, organoId, settlement.state(), settlement.reason());
    } catch (RuntimeException e) {
      LOG.warn(
          "Contratos menores import of Órgano {} ended as {} but run {} does not record it; the"
              + " contracts stand and the run carries on",
          organoId, settlement.state(), runId, e);
    }
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

  private static String reasonOf(RuntimeException e) {
    String message = e.getMessage();
    return message == null ? e.getClass().getSimpleName() : message;
  }
}
