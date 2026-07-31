package gal.conxugal.acceptance.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Sessions against the running instance: the accounts its migrations seed, and the
 * {@code SESSION} cookie a login exchanges their credentials for.
 */
public final class ApplicationSession {

  public static final String ADMIN_EMAIL = "root@local";
  public static final String ADMIN_PASSWORD = "secret";
  public static final String USER_EMAIL = "demo@local";
  public static final String USER_PASSWORD = "demo";

  private static final String SESSION_COOKIE_NAME = "SESSION";

  private ApplicationSession() {
  }

  /**
   * A request that identifies itself as a JSON API caller, so a denied route answers with a
   * status code instead of redirecting to the login page, and that never follows redirects —
   * a successful login answers with one, and it is that response which carries the cookie.
   */
  public static RequestSpecification anonymous() {
    return given()
        .baseUri(ApplicationUnderTest.BASE_URI)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .redirects().follow(false);
  }

  public static RequestSpecification authenticatedAs(String sessionCookie) {
    return anonymous().header("Cookie", sessionCookie);
  }

  /**
   * The instance's whole answer to a login attempt, so a scenario can assert how a refusal
   * looks and not merely that no session came back.
   */
  public static Response logInResponse(String email, String password) {
    return anonymous()
        .body(
            """
            {"username":"%s","password":"%s"}\
            """.formatted(email, password))
    .when()
        .post("/login");
  }

  /** The session cookie a login answer carries, or empty when it granted none. */
  public static Optional<String> sessionCookieOf(Response loginResponse) {
    return loginResponse.getHeaders()
        .getValues("Set-Cookie")
        .stream()
        .map(setCookie -> setCookie.split(";", 2)[0])
        .filter(cookiePair -> cookiePair.startsWith("%s=".formatted(SESSION_COOKIE_NAME)))
        .findFirst();
  }

  public static String logInOrFail(String email, String password) {
    return sessionCookieOf(logInResponse(email, password))
        .orElseThrow(
            () -> new NoSuchElementException("Login refused for %s".formatted(email)));
  }
}
