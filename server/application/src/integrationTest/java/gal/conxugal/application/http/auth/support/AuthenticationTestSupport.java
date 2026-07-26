package gal.conxugal.application.http.auth.support;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.user.PasswordEncoder;
import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserRepository;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.test.annotation.MockBean;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public abstract class AuthenticationTestSupport {

  private static final String SESSION_COOKIE_NAME = "SESSION";

  @Inject
  protected UserRepository userRepository;

  protected static RequestSpecification given(RequestSpecification spec) {
    return RestAssured.given()
        .spec(spec)
        .redirects().follow(false)
        .contentType(MediaType.APPLICATION_JSON);
  }

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

  protected String loginAs(RequestSpecification spec, User user) {
    Response response =
        given(spec)
            .body(
                """
                {"username":"%s","password":"%s"}\
                """.formatted(user.email(), user.passwordHash()))
        .when()
            .post("/login");
    return sessionCookieOf(response);
  }

  protected static String sessionCookieOf(HttpResponse<?> response) {
    return sessionCookiePairOf(response.getHeaders().getAll(HttpHeaders.SET_COOKIE));
  }

  protected static String sessionCookieOf(Response response) {
    return sessionCookiePairOf(response.getHeaders().getValues(HttpHeaders.SET_COOKIE));
  }

  private static String sessionCookiePairOf(List<String> setCookieHeaders) {
    return setCookieHeaders.stream()
        .map(setCookieHeader -> setCookieHeader.split(";", 2)[0])
        .filter(cookiePair -> cookiePair.startsWith(SESSION_COOKIE_NAME + "="))
        .findFirst()
        .orElseThrow(
            () -> new NoSuchElementException(
                SESSION_COOKIE_NAME + " cookie not present in Set-Cookie headers"));
  }
}
