package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.importrun.ContractFamily;
import gal.conxugal.domain.importrun.CoveredOrgano;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Who a run would cover, and whether it may start at all. */
@ExtendWith(MockitoExtension.class)
class ClaimContratosMenoresImportTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final OrganoId FIRST = new OrganoId(UUID.randomUUID());
  private static final OrganoId SECOND = new OrganoId(UUID.randomUUID());

  @Mock
  private OrganoRepository organos;

  @Mock
  private ImportRunRepository importRuns;

  @Test
  void claims_one_run_covering_every_active_and_marked_organo_for_contratos_menores_alone() {
    when(organos.findAllByActiveTrueAndImportableTrue())
        .thenReturn(List.of(marked(FIRST), marked(SECOND)));
    // Stubbed on the exact coverage: strict stubbing refuses any other list, so this is what
    // proves the run was claimed over these two Órganos, for this family only, and no others.
    when(importRuns.claim(
            Importer.CONTRATOS_MENORES,
            List.of(contratosMenoresOf(FIRST), contratosMenoresOf(SECOND))))
        .thenReturn(Optional.of(RUN_ID));

    assertThat(claimContratosMenoresImport().claimAll()).isEqualTo(RUN_ID);
  }

  // A catalogue with nothing marked is an ordinary answer, not a refusal: the run records that it
  // was asked and covered nothing, which is what an administrator seeing no import needs.
  @Test
  void claims_one_run_covering_nothing_when_no_organo_is_marked() {
    when(organos.findAllByActiveTrueAndImportableTrue()).thenReturn(List.of());
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of())).thenReturn(Optional.of(RUN_ID));

    assertThat(claimContratosMenoresImport().claimAll()).isEqualTo(RUN_ID);
  }

  @Test
  void refuses_the_sweep_when_another_import_holds_the_guard() {
    when(organos.findAllByActiveTrueAndImportableTrue()).thenReturn(List.of(marked(FIRST)));
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of(contratosMenoresOf(FIRST))))
        .thenReturn(Optional.empty());
    ClaimContratosMenoresImport claim = claimContratosMenoresImport();

    assertThatExceptionOfType(ImportAlreadyRunningException.class).isThrownBy(claim::claimAll);
  }

  @Test
  void claims_one_run_covering_only_the_named_organo() {
    when(organos.findById(SECOND)).thenReturn(Optional.of(marked(SECOND)));
    when(importRuns.claim(Importer.CONTRATOS_MENORES, List.of(contratosMenoresOf(SECOND))))
        .thenReturn(Optional.of(RUN_ID));

    assertThat(claimContratosMenoresImport().claimOrgano(SECOND)).isEqualTo(RUN_ID);
  }

  // The guard is never touched: an ineligible Órgano leaves no run row and no evidence of having
  // been asked for, and the refusal it gets is its own rather than whichever one the guard had.
  @Test
  void refuses_the_named_organo_that_is_not_marked_without_touching_the_guard() {
    when(organos.findById(FIRST)).thenReturn(Optional.of(organo(FIRST, true, false)));
    ClaimContratosMenoresImport claim = claimContratosMenoresImport();

    assertThatExceptionOfType(OrganoNotEligibleForImportException.class)
        .isThrownBy(() -> claim.claimOrgano(FIRST))
        .satisfies(refusal -> assertThat(refusal.getOrganoId()).isEqualTo(FIRST));

    verifyNoInteractions(importRuns);
  }

  @Test
  void refuses_the_named_organo_that_is_no_longer_active() {
    when(organos.findById(FIRST)).thenReturn(Optional.of(organo(FIRST, false, true)));
    ClaimContratosMenoresImport claim = claimContratosMenoresImport();

    assertThatExceptionOfType(OrganoNotEligibleForImportException.class)
        .isThrownBy(() -> claim.claimOrgano(FIRST));

    verifyNoInteractions(importRuns);
  }

  @Test
  void throws_when_the_named_organo_is_unknown() {
    when(organos.findById(FIRST)).thenReturn(Optional.empty());
    ClaimContratosMenoresImport claim = claimContratosMenoresImport();

    assertThatExceptionOfType(OrganoNotFoundException.class)
        .isThrownBy(() -> claim.claimOrgano(FIRST));

    verifyNoInteractions(importRuns);
  }

  private static CoveredOrgano contratosMenoresOf(OrganoId organoId) {
    return new CoveredOrgano(organoId, ContractFamily.CONTRATOS_MENORES);
  }

  private ClaimContratosMenoresImport claimContratosMenoresImport() {
    return new ClaimContratosMenoresImport(organos, importRuns);
  }

  private static OrganoDeContratacion marked(OrganoId organoId) {
    return organo(organoId, true, true);
  }

  private static OrganoDeContratacion organo(
      OrganoId organoId, boolean active, boolean importable) {
    return new OrganoDeContratacion(
        organoId, "source-key", "Órgano", active, importable, null, null);
  }
}
