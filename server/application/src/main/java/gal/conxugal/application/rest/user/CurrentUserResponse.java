package gal.conxugal.application.rest.user;

import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserId;
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
    @Nullable UUID id, String email, Role role, Instant createdAt, @Nullable Instant lastLoginAt) {

  static CurrentUserResponse of(User user) {
    UserId id = user.id();
    return new CurrentUserResponse(
        id == null ? null : id.value(), user.email(), user.role(), user.createdAt(),
        user.lastLoginAt());
  }
}
