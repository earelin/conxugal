package gal.conxugal.domain.user;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A registered user: their identity (email), stored password hash, role, enabled
 * state and creation date. {@code passwordHash} is never plaintext. {@code id} is
 * assigned by the database (its {@code DEFAULT uuidv7()}) on insert, so it is
 * {@code null} only until that assignment happens. {@code lastLoginAt} is {@code null}
 * until the user's first successful login.
 */
@MappedEntity("users")
public record User(
    @Id @GeneratedValue @Nullable UserId id,
    String email,
    String passwordHash,
    Role role,
    boolean enabled,
    Instant createdAt,
    @Nullable Instant lastLoginAt) {

  public User {
    Objects.requireNonNull(email, "email must not be null");
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    Objects.requireNonNull(role, "role must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  public User(
      @Nullable UserId id, String email, String passwordHash, Role role, boolean enabled,
      Instant createdAt) {
    this(id, email, passwordHash, role, enabled, createdAt, null);
  }

  /**
   * Identity, not value: a user who has changed password, role or enabled state is still the same
   * user, and the hash stays out of every comparison. One the database has not yet assigned an id
   * to is equal to nothing but itself.
   */
  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof User other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return ("User[id=%s, email=%s, passwordHash=REDACTED, role=%s, enabled=%s, "
            + "createdAt=%s, lastLoginAt=%s]")
        .formatted(id, email, role, enabled, createdAt, lastLoginAt);
  }
}
