package gal.conxugal.application.auth.support;

import gal.conxugal.domain.auth.PasswordEncoder;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@Singleton
@Replaces(PasswordEncoder.class)
public class PlaintextPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(String rawPassword) {
        return rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword.equals(encodedPassword);
    }

    @Override
    public void matchAgainstDummyHash(String rawPassword) {
    }
}
