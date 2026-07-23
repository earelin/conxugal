package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.UUID;

/** An account's administration state — never carries {@code passwordHash}. */
@Serdeable
public record UserResponse(
    UUID id, String email, Role role, boolean enabled, Instant createdAt,
    @Nullable Instant lastLoginAt) {

  static UserResponse of(User user) {
    return new UserResponse(
        user.id(), user.email(), user.role(), user.enabled(), user.createdAt(),
        user.lastLoginAt());
  }
}
