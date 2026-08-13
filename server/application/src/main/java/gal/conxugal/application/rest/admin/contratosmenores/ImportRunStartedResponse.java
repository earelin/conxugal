package gal.conxugal.application.rest.admin.contratosmenores;

import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * All a trigger can answer with: which run was claimed. The import it started has done nothing
 * yet, and {@code GET /api/admin/import-run/{id}} takes this identifier verbatim.
 */
@Serdeable
public record ImportRunStartedResponse(UUID runId) {}
