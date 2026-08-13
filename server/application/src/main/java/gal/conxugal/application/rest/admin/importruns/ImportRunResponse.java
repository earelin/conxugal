package gal.conxugal.application.rest.admin.importruns;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.importrun.ImportRunReport;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One run as a reader sees it: what it amounts to, when it ran, what it counted, and every Órgano
 * it set out to cover.
 *
 * <p>Deliberately not the catalogue import's outcome shape. That one knows two verdicts and caps
 * its counts at 100 000, which a single Órgano's initial import passes by an order of magnitude.
 *
 * <p>The states travel as their names rather than as the enums they came from. The abandoned
 * verdict is derived in exactly one place and stored nowhere, and a serialisation naming the
 * constants would be a second place holding it — the same reason the read above does not
 * re-derive it.
 *
 * <p>Nulls are sent explicitly: a run still going has no finish time, and that is an answer rather
 * than a missing field.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ImportRunResponse(
    UUID id,
    String importer,
    String state,
    Instant startedAt,
    @Nullable Instant finishedAt,
    int added,
    int refreshed,
    List<ImportRunOrganoResponse> coveredOrganos) {

  public ImportRunResponse {
    coveredOrganos = List.copyOf(coveredOrganos);
  }

  static ImportRunResponse of(ImportRunReport report) {
    return new ImportRunResponse(
        report.id().value(),
        report.importer().name(),
        report.state().name(),
        report.startedAt(),
        report.finishedAt(),
        report.added(),
        report.refreshed(),
        report.coveredOrganos().stream().map(ImportRunOrganoResponse::of).toList());
  }
}
