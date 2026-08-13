package gal.conxugal.domain.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.OrganoId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadImportRunTest {

  private static final ImportRunId RUN = new ImportRunId(UUID.randomUUID());

  @Mock
  private ImportRunRepository importRunRepository;

  private ReadImportRun readImportRun;

  @BeforeEach
  void setUp() {
    readImportRun = new ReadImportRun(importRunRepository);
  }

  @Test
  void returns_the_run_with_every_organo_it_covers() {
    ImportRunReport report =
        new ImportRunReport(
            RUN, Importer.CONTRATOS_MENORES, ImportRunState.PARTIALLY_SUCCEEDED,
            Instant.parse("2026-08-13T09:14:02Z"), Instant.parse("2026-08-15T21:40:55Z"), 1204, 96,
            List.of(
                new ImportRunOrganoCoverage(
                    new OrganoId(UUID.randomUUID()), ImportRunOrganoState.SUCCEEDED, 1204, 96,
                    null)));
    when(importRunRepository.findRun(RUN)).thenReturn(Optional.of(report));

    Optional<ImportRunReport> result = readImportRun.read(RUN);

    assertThat(result).contains(report);
  }

  @Test
  void returns_nothing_when_no_run_has_that_identity() {
    when(importRunRepository.findRun(RUN)).thenReturn(Optional.empty());

    Optional<ImportRunReport> result = readImportRun.read(RUN);

    assertThat(result).isEmpty();
  }
}
