package gal.conxugal.acceptance.support;

/**
 * The base URL of the running {@code application} instance under test. The instance —
 * and any downstream mocks it's configured to call — is expected to already be
 * running externally (docker-compose, a deployed environment, or started manually
 * for a local run) before the suite executes; this module only knows its address.
 */
public final class ApplicationUnderTest {

  public static final String DEFAULT_BASE_URI = "http://localhost:8080";
  public static final String BASE_URI = System.getProperty("app.baseUrl", DEFAULT_BASE_URI);

  private ApplicationUnderTest() {
  }
}
