package gal.conxugal.application.rest.admin.organos;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A term's replacement name. */
@Serdeable
public record RenameTermoRequest(@NotBlank @Size(max = 255) String name) {
}
