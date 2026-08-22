package gal.conxugal.domain.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.organo.OrganoId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a coverage row must carry, and the one thing it need not.
 *
 * <p>Refusing at construction is what keeps the failure where the mistake was made. The row is
 * written from inside the claim's transaction — the adapter binds {@code organoId.value()},
 * {@code family.name()} and {@code state.name()} — so a missing one would surface as a rolled-back
 * claim that took the import guard down with it, rather than as a caller passing null.
 */
class ImportRunOrganoCoverageTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());

  @Test
  void rejects_the_coverage_that_names_no_organo() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ImportRunOrganoCoverage(
                    null,
                    ContractFamily.CONTRATOS_MENORES,
                    ImportRunOrganoState.PENDING,
                    0,
                    0,
                    null))
        .withMessageContaining("organoId");
  }

  // Since one run covers an Órgano once per family, a row without one is a row nothing can address:
  // advance and finishOrgano both name the family to reach it.
  @Test
  void rejects_the_coverage_that_names_no_family() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ImportRunOrganoCoverage(
                    ORGANO_ID, null, ImportRunOrganoState.PENDING, 0, 0, null))
        .withMessageContaining("family");
  }

  @Test
  void rejects_the_coverage_that_names_no_state() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ImportRunOrganoCoverage(
                    ORGANO_ID, ContractFamily.CONTRATOS_MENORES, null, 0, 0, null))
        .withMessageContaining("state");
  }

  // The one component that may be absent, and the ordinary case rather than the exception: a row
  // that succeeded has nothing to explain, and only a failed, skipped or stopped one does.
  @Test
  void keeps_the_coverage_that_gives_no_failure_reason() {
    assertThat(succeeded(ContractFamily.CONTRATOS_MENORES).failureReason()).isNull();
  }

  /**
   * The family is part of what a coverage row <em>is</em>, not a label on it. {@link ImportRun} in
   * this package narrows its own equality to its identity, so this is a real thing to get wrong:
   * a coverage identified by its Órgano alone would make a run's two rows one, which is exactly the
   * shape the per-family key exists to admit.
   */
  @Test
  void tells_the_two_families_of_one_organo_apart() {
    assertThat(succeeded(ContractFamily.CONTRATOS_MENORES))
        .isNotEqualTo(succeeded(ContractFamily.LICITACIONS));
  }

  private static ImportRunOrganoCoverage succeeded(ContractFamily family) {
    return new ImportRunOrganoCoverage(
        ORGANO_ID, family, ImportRunOrganoState.SUCCEEDED, 1204, 96, null);
  }
}
