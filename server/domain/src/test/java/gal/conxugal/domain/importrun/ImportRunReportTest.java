package gal.conxugal.domain.importrun;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.organo.OrganoId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportRunReportTest {

  private static final Instant STARTED_AT = Instant.parse("2026-08-07T09:00:00Z");

  @Test
  void holds_its_own_copy_of_the_covered_organos() {
    List<ImportRunOrganoCoverage> covered = new ArrayList<>();
    covered.add(coverage());
    ImportRunReport report = reportCovering(covered);

    covered.clear();

    assertThat(report.coveredOrganos()).hasSize(1);
  }

  @Test
  void run_covering_no_organos_covers_none() {
    assertThat(reportCovering(List.of()).coveredOrganos()).isEmpty();
  }

  private static ImportRunReport reportCovering(List<ImportRunOrganoCoverage> covered) {
    return new ImportRunReport(
        new ImportRunId(UUID.randomUUID()),
        Importer.CONTRATOS_MENORES,
        ImportRunState.IN_PROGRESS,
        STARTED_AT,
        null,
        0,
        0,
        covered);
  }

  private static ImportRunOrganoCoverage coverage() {
    return new ImportRunOrganoCoverage(
        new OrganoId(UUID.randomUUID()),
        ContractFamily.CONTRATOS_MENORES,
        ImportRunOrganoState.PENDING,
        0,
        0,
        null);
  }
}
