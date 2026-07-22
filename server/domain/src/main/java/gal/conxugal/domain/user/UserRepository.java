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

  Optional<User> findById(UUID id);

  Optional<User> findByEmail(String email);

  List<User> findAll();

  long countByRoleAndEnabled(Role role, boolean enabled);

  /**
   * Locks and returns every account with the given role and enabled state ({@code SELECT
   * ... FOR UPDATE}), so a concurrent call for the same role/state blocks until this
   * transaction commits. Used to serialize concurrent {@code enabled} changes against the
   * last-enabled-admin guard.
   */
  List<User> findByRoleAndEnabledForUpdate(Role role, boolean enabled);

  void updateLastLoginAt(@Id UUID id, Instant lastLoginAt);

  void updateEnabled(@Id UUID id, boolean enabled);

  /**
   * Persists a new account, using the {@code createdAt} the domain supplied. The returned
   * instance carries the id the database generated; {@code user.id()} is ignored.
   */
  @Insert
  User create(User user);
}
