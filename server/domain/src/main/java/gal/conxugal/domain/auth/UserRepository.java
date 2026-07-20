package gal.conxugal.domain.auth;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for looking up, listing and updating users. Implemented by the
 * {@code infrastructure} module.
 */
public interface UserRepository {

  Optional<User> findByEmail(String email);

  List<User> findAll();

  void updateLastLoginAt(@Id UUID id, Instant lastLoginAt);

  void updateEnabled(@Id UUID id, boolean enabled);

  @Insert
  User create(User user);
}
