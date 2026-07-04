package gal.conxugal.domain.auth;

import java.util.Optional;

/**
 * Port for looking up users by email. Implemented by the {@code infrastructure}
 * module (ADR-0002).
 */
public interface UserRepository {

    Optional<User> findByEmail(String email);
}
