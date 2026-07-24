package gal.conxugal.application.rest.user;

import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.UUID;

/**
 * The authenticated caller's own account — never carries {@code enabled} or
 * {@code passwordHash}.
 */
@Serdeable
public record CurrentUserResponse(
    UUID id, String email, Role role, Instant createdAt, @Nullable Instant lastLoginAt) {

  static CurrentUserResponse of(User user) {
    return new CurrentUserResponse(
        user.id(), user.email(), user.role(), user.createdAt(), user.lastLoginAt());
  }
}
