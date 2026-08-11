package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.importrun.ImportRunId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContratosMenoresImportClaimTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());

  @Test
  void rejects_null_status() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ContratosMenoresImportClaim(null, null))
        .withMessageContaining("status");
  }

  @Test
  void carries_the_run_identity_of_the_import_that_was_claimed() {
    ContratosMenoresImportClaim claim = ContratosMenoresImportClaim.claimed(RUN_ID);

    assertThat(claim.status()).isEqualTo(ContratosMenoresImportClaim.Status.CLAIMED);
    assertThat(claim.runId()).isEqualTo(RUN_ID);
  }

  /**
   * The invariant a caller leans on: a claimed import is the one case where the identity is there
   * to be used, so it may read it without asking whether it is null.
   */
  @Test
  void rejects_the_claimed_import_that_carries_no_run_identity() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ContratosMenoresImportClaim(
                    ContratosMenoresImportClaim.Status.CLAIMED, null));
  }

  @Test
  void refuses_to_claim_an_import_without_run_identity() {
    assertThatNullPointerException()
        .isThrownBy(() -> ContratosMenoresImportClaim.claimed(null))
        .withMessageContaining("runId");
  }

  /** A refusal started no run, so there is no identity for it to be carrying. */
  @Test
  void rejects_the_guard_refusal_that_carries_run_identity() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ContratosMenoresImportClaim(
                    ContratosMenoresImportClaim.Status.ALREADY_RUNNING, RUN_ID));
  }

  @Test
  void rejects_the_not_eligible_refusal_that_carries_run_identity() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ContratosMenoresImportClaim(
                    ContratosMenoresImportClaim.Status.NOT_ELIGIBLE, RUN_ID));
  }

  @Test
  void guard_refusal_carries_no_run_identity() {
    ContratosMenoresImportClaim claim = ContratosMenoresImportClaim.alreadyRunning();

    assertThat(claim.status()).isEqualTo(ContratosMenoresImportClaim.Status.ALREADY_RUNNING);
    assertThat(claim.runId()).isNull();
  }

  @Test
  void not_eligible_refusal_carries_no_run_identity() {
    ContratosMenoresImportClaim claim = ContratosMenoresImportClaim.notEligible();

    assertThat(claim.status()).isEqualTo(ContratosMenoresImportClaim.Status.NOT_ELIGIBLE);
    assertThat(claim.runId()).isNull();
  }

  /**
   * The reason this type exists rather than an empty answer. An administrator told only that an
   * import is running would wait for one that is never going to start, so the two refusals must
   * never compare equal however alike they look — both carry no identity and no counts.
   */
  @Test
  void tells_the_two_refusals_apart() {
    assertThat(ContratosMenoresImportClaim.alreadyRunning())
        .isNotEqualTo(ContratosMenoresImportClaim.notEligible());
  }
}
