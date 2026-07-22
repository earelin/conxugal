package gal.conxugal.application.http.admin.users;

import gal.conxugal.domain.user.Role;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The email and role for a new account; the server generates the initial password. */
@Serdeable
public record CreateUserRequest(
    @NotBlank @Email String email,
    @NotNull Role role) {
}
