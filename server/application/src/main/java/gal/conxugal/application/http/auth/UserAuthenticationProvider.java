package gal.conxugal.application.http.auth;

import gal.conxugal.domain.auth.Authenticate;
import gal.conxugal.domain.auth.User;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class UserAuthenticationProvider implements HttpRequestAuthenticationProvider<Object> {

    private final Authenticate authenticate;

    public UserAuthenticationProvider(Authenticate authenticate) {
        this.authenticate = authenticate;
    }

    @Blocking
    @Override
    public AuthenticationResponse authenticate(HttpRequest<Object> requestContext,
                                                AuthenticationRequest<String, String> authRequest) {
        Optional<User> user = authenticate.authenticate(authRequest.getIdentity(), authRequest.getSecret());

        return user
                .<AuthenticationResponse>map(u -> AuthenticationResponse.success(u.email(), List.of(u.role().name())))
                .orElseGet(() -> AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH));
    }
}
