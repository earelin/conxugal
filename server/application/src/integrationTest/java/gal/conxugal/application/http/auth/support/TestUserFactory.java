package gal.conxugal.application.http.auth.support;

import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;

/** Builds {@link User} instances for integration tests seeding {@code UserRepository}. */
public final class TestUserFactory {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private TestUserFactory() {
  }

  public static User normalUser() {
    return new User(new UserId(UUID.randomUUID()), "user@example.com", "user-password",
        Role.USER, true, CREATED_AT);
  }

  public static User adminUser() {
    return new User(new UserId(UUID.randomUUID()), "admin@example.com", "admin-password",
        Role.ADMIN, true, CREATED_AT);
  }
}
