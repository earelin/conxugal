package gal.conxugal.domain.user;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port for looking up, listing and updating users. Implemented by the
 * {@code infrastructure} module.
 */
public interface UserRepository {

  Optional<User> findById(UserId id);

  Optional<User> findByEmail(String email);

  List<User> findAll();

  /**
   * Locks and returns every account with the given role and enabled state ({@code SELECT
   * ... FOR UPDATE}), so a concurrent call for the same role/state blocks until this
   * transaction commits. Used to serialize concurrent {@code enabled} changes against the
   * last-enabled-admin guard.
   */
  List<User> findByRoleAndEnabledForUpdate(Role role, boolean enabled);

  void updateLastLoginAt(@Id UserId id, Instant lastLoginAt);

  void updateEnabled(@Id UserId id, boolean enabled);

  /**
   * Persists a new account, using the {@code createdAt} the domain supplied. The returned
   * instance carries the id the database generated; {@code user.id()} is ignored.
   */
  @Insert
  User create(User user);
}
