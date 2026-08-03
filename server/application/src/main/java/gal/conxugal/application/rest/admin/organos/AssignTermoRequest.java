package gal.conxugal.application.rest.admin.organos;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The term to file an Órgano under. Unlike a term's parent, this one is required: an Órgano
 * is returned to the unclassified set by deleting the placement, not by assigning a null.
 */
@Serdeable
public record AssignTermoRequest(@NotNull UUID termoId) {
}
