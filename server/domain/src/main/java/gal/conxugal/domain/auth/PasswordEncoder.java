package gal.conxugal.domain.auth;

/**
 * Port for hashing and verifying passwords (SPEC-0002 #11, #12). Implemented by a
 * salted-hash adapter in the {@code infrastructure} module (ADR-0005); the adapter is
 * introduced in TASK-0002.
 *
 * <p>{@link #matches} must return {@code false} rather than throwing for an
 * {@code encodedPassword} it does not recognise, so callers can compare against a
 * placeholder hash for an unknown user without special-casing that path (SPEC-0002 #3).
 */
public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
