package gal.conxugal.domain.auth;

import java.util.Optional;

/**
 * Port for looking up users by email. Implemented by the {@code infrastructure}
 * module.
 */
public interface UserRepository {

  Optional<User> findByEmail(String email);
}
