package gal.conxugal.domain.auth;

import java.util.Objects;

/**
 * A registered user: their identity (email), stored password hash, and role
 * (SPEC-0002). {@code passwordHash} is never plaintext (SPEC-0002 #9).
 */
public record User(String email, String passwordHash, Role role) {

    public User {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    @Override
    public String toString() {
        return "User[email=%s, passwordHash=REDACTED, role=%s]".formatted(email, role);
    }
}
