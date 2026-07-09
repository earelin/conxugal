package gal.conxugal.application.http.auth.support;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.auth.PasswordEncoder;
import gal.conxugal.domain.auth.User;
import gal.conxugal.domain.auth.UserRepository;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.test.annotation.MockBean;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;

public abstract class AuthenticationTestSupport {

  @Inject
  protected UserRepository userRepository;

  @MockBean(UserRepository.class)
  protected UserRepository userRepository() {
    return mock(UserRepository.class);
  }

  @MockBean(PasswordEncoder.class)
  protected PasswordEncoder passwordEncoder() {
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(passwordEncoder.matches(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class)
            .equals(invocation.getArgument(1, String.class)));
    return passwordEncoder;
  }

  protected void seedUser(User user) {
    when(userRepository.findByEmail(user.email())).thenReturn(Optional.of(user));
  }

  protected static String sessionCookieOf(HttpResponse<?> response) {
    String setCookieHeader = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    Objects.requireNonNull(setCookieHeader, "Set-Cookie header must be present");
    return setCookieHeader.split(";", 2)[0];
  }

  protected static String sessionCookieOf(Response response) {
    String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
    Objects.requireNonNull(setCookieHeader, "Set-Cookie header must be present");
    return setCookieHeader.split(";", 2)[0];
  }
}
