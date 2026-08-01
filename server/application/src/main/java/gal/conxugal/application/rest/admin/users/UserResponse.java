package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.UUID;

/** An account's administration state — never carries {@code passwordHash}. */
@Serdeable
public record UserResponse(
    @Nullable UUID id, String email, Role role, boolean enabled, Instant createdAt,
    @Nullable Instant lastLoginAt) {

  static UserResponse of(User user) {
    UserId id = user.id();
    return new UserResponse(
        id == null ? null : id.value(), user.email(), user.role(), user.enabled(),
        user.createdAt(), user.lastLoginAt());
  }
}
