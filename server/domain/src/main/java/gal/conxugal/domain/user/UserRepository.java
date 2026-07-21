package gal.conxugal.domain.user;

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

  List<User> findByRole(UUID id);

  List<User> findAll();

  void updateLastLoginAt(@Id UUID id, Instant lastLoginAt);

  void updateEnabled(@Id UUID id, boolean enabled);

  /** Persists a new account, including the id and {@code createdAt} the domain supplied. */
  @Insert
  User create(User user);
}
