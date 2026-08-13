package gal.conxugal.application.rest.admin.organos;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * What marking an Órgano did: the mark, which is always written, and the fate of the import it
 * asked for. Exactly one of the two fields is set.
 *
 * <p>Both are sent explicitly rather than omitted when null, because which of them is null is the
 * answer the caller came for.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MarkOutcomeResponse(@Nullable UUID runId, @Nullable ImportRefusal refusal) {

  static MarkOutcomeResponse started(UUID runId) {
    return new MarkOutcomeResponse(runId, null);
  }

  static MarkOutcomeResponse refused(ImportRefusal refusal) {
    return new MarkOutcomeResponse(null, refusal);
  }
}
