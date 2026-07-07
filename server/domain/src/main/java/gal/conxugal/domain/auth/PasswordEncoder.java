package gal.conxugal.domain.auth;

/**
 * Port for hashing and verifying passwords. Implemented by a salted-hash adapter in
 * the {@code infrastructure} module.
 */
public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);

    /**
     * Compares {@code rawPassword} against a predefined hash chosen by the adapter,
     * taking the same time a real {@link #matches} call would. Used when no user was
     * found, so execution time does not disclose whether the email was unknown or the
     * password was wrong.
     */
    void matchAgainstDummyHash(String rawPassword);
}
