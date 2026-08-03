package gal.conxugal.application.rest.admin.organos;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The name for a new term and the term it sits under. A null {@code parentId} is a value
 * rather than an omission — it places the term at the root — so it carries no
 * {@code @NotNull}: rejecting it would make a root term unexpressible.
 */
@Serdeable
public record CreateTermoRequest(
    @NotBlank @Size(max = 255) String name,
    @Nullable UUID parentId) {
}
