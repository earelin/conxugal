package gal.conxugal.domain.auth;

import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Authenticate use case (SPEC-0002 #1-#3, ADR-0005): finds the user by email and
 * verifies the password against the stored hash, returning the user on success or an
 * indistinct failure otherwise.
 *
 * <p>The password check always runs, even for an unknown email, comparing against
 * {@link #UNKNOWN_USER_PLACEHOLDER_HASH} instead of short-circuiting — so an unknown
 * email and a wrong password are not separable (SPEC-0002 #3).
 */
@Singleton
public class Authenticate {

    private static final String UNKNOWN_USER_PLACEHOLDER_HASH = "";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Authenticate(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> authenticate(String email, String password) {
        Optional<User> user = userRepository.findByEmail(email);
        String hashToVerify = user.map(User::passwordHash).orElse(UNKNOWN_USER_PLACEHOLDER_HASH);
        boolean passwordMatches = passwordEncoder.matches(password, hashToVerify);

        return user.filter(ignored -> passwordMatches);
    }
}
