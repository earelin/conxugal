package gal.conxugal.domain.auth;

import io.micronaut.data.annotation.Id;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for looking up and updating users. Implemented by the {@code infrastructure}
 * module.
 */
public interface UserRepository {

  Optional<User> findByEmail(String email);

  void updateLastLoginAt(@Id UUID id, Instant lastLoginAt);
}
