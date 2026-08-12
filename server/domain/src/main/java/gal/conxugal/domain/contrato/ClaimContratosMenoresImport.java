package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether a contratos menores import may start, and which Órganos it would cover.
 *
 * <p>Short and synchronous, and separate from the walking for that reason: this answers in
 * milliseconds and its answer is what a trigger waits for, while what follows it runs for days.
 * Nothing here reads a source or writes a contract — it settles eligibility, takes the guard and
 * enumerates the coverage, and every one of those is over before the first Órgano is touched.
 *
 * <p><strong>Eligibility is decided here and nowhere else.</strong> Every trigger — a mark, an
 * administrator's button, a future scheduler — asks the same question of the same code, so none of
 * them can disagree with the others about which Órganos an import covers.
 *
 * <p><strong>The covered Órganos are fixed now, not discovered as the run reaches them</strong>, so
 * a run that dies at the fortieth of four hundred can still name what it set out to cover.
 *
 * <p><strong>Refusals are exceptions</strong>, so a caller cannot read past one to an identity that
 * does not exist. There are two and they are separate types, because they ask opposite things of an
 * administrator: the guard being held is a matter of timing and asking again later works, while an
 * ineligible Órgano refuses every time until the catalogue or the mark changes.
 */
@Singleton
public class ClaimContratosMenoresImport {

  private final OrganoRepository organos;
  private final ImportRunRepository importRuns;

  public ClaimContratosMenoresImport(OrganoRepository organos, ImportRunRepository importRuns) {
    this.organos = organos;
    this.importRuns = importRuns;
  }

  /**
   * Claims a run over every eligible Órgano and answers its identity.
   *
   * <p>One covering none is still a run: the catalogue having nothing marked is an ordinary answer,
   * not a refusal, and it settles as a success that imported nothing.
   *
   * @throws ImportAlreadyRunningException if another import holds the guard, in which case nothing
   *     was claimed
   */
  public ImportRunId claimAll() {
    return claimCovering(
        organos.findAllByActiveTrueAndImportableTrue().stream()
            .map(ClaimContratosMenoresImport::identityOf)
            .toList());
  }

  /**
   * Claims a run over one named Órgano and answers its identity.
   *
   * <p>Eligibility is settled before the guard is touched, so an Órgano that cannot be imported
   * claims no run and leaves no trace of having asked — and it is told apart from the guard being
   * held, because one refusal repeats until the catalogue or the mark changes while the other is a
   * matter of timing.
   *
   * @throws OrganoNotFoundException if no Órgano has this identity
   * @throws OrganoNotEligibleForImportException if it is not active in the catalogue and marked
   * @throws ImportAlreadyRunningException if another import holds the guard
   */
  public ImportRunId claimOrgano(OrganoId organoId) {
    OrganoDeContratacion organo =
        organos.findById(organoId).orElseThrow(() -> new OrganoNotFoundException(organoId));
    if (!organo.eligibleForImport()) {
      throw new OrganoNotEligibleForImportException(organoId);
    }
    return claimCovering(List.of(organoId));
  }

  private ImportRunId claimCovering(List<OrganoId> covered) {
    return importRuns
        .claim(Importer.CONTRATOS_MENORES, covered)
        .orElseThrow(ImportAlreadyRunningException::new);
  }

  private static OrganoId identityOf(OrganoDeContratacion organo) {
    return Objects.requireNonNull(organo.id(), "a stored Órgano always carries its identity");
  }
}
