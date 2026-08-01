package gal.conxugal.application.rest.admin.users;

import static java.util.Objects.requireNonNull;

import gal.conxugal.domain.user.CreatedAccount;
import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.UUID;

/** The account just created, including its one-time initial password. */
@Serdeable
public record CreatedUserResponse(
    UUID id, String email, Role role, boolean enabled, Instant createdAt,
    String initialPassword) {

  static CreatedUserResponse of(CreatedAccount createdAccount) {
    User user = createdAccount.user();
    return new CreatedUserResponse(
        requireNonNull(user.id(), "a persisted user must carry an id").value(), user.email(),
        user.role(), user.enabled(), user.createdAt(),
        createdAccount.initialPassword().value());
  }

  @Override
  public String toString() {
    return ("CreatedUserResponse[id=%s, email=%s, role=%s, enabled=%s, createdAt=%s, "
            + "initialPassword=REDACTED]")
        .formatted(id, email, role, enabled, createdAt);
  }
}
