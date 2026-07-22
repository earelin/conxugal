package gal.conxugal.application.http.admin.users;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/** The desired enabled state for an account. */
@Serdeable
public record SetEnabledRequest(@NotNull Boolean enabled) {
}
