package gal.conxugal.acceptance;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import gal.conxugal.acceptance.support.ApplicationUnderTest;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the packaged server with a real Chromium browser, proving the whole routing
 * matrix (SPA fallback, reserved {@code /api/} prefix, static-asset resolution) holds
 * against the actual built UI assets, not a mock.
 */
class AuthenticatedSpaRoutingTest {

  private static final String DEMO_EMAIL = "demo@local";
  private static final String DEMO_PASSWORD = "demo";
  private static final String ROOT_URL = ApplicationUnderTest.BASE_URI + "/";
  private static final String LOGIN_URL = ApplicationUnderTest.BASE_URI + "/login";

  private static Playwright playwright;
  private static Browser browser;

  private BrowserContext context;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  @AfterAll
  static void closeBrowser() {
    browser.close();
    playwright.close();
  }

  @BeforeEach
  void openContext() {
    context = browser.newContext();
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  @Test
  void demo_user_logs_in_and_the_spa_routing_matrix_holds_against_the_packaged_ui() {
    try (Page page = context.newPage()) {
      logInAsDemoUser(page);

      Response rootResponse = page.navigate(ROOT_URL);
      assertThat(rootResponse.status()).isEqualTo(200);
      assertThat(rootResponse.headerValue("content-type")).contains("text/html");
      assertThat(page.getByText("Benvido/a a conxugal")).isVisible();
      assertBuiltAssetsResolve(page);

      Response acercaResponse = page.navigate(ApplicationUnderTest.BASE_URI + "/acerca");
      assertThat(acercaResponse.status()).isEqualTo(200);
      assertThat(acercaResponse.headerValue("content-type")).contains("text/html");
      assertThat(page.getByText("Acerca do proxecto")).isVisible();

      Response unknownRouteResponse =
          page.navigate(ApplicationUnderTest.BASE_URI + "/rota-que-non-existe");
      assertThat(unknownRouteResponse.status()).isEqualTo(200);
      assertThat(unknownRouteResponse.headerValue("content-type")).contains("text/html");
      assertThat(page.getByText("Páxina non atopada")).isVisible();

      APIResponse unknownApiResponse =
          context.request().get(ApplicationUnderTest.BASE_URI + "/api/rota-que-non-existe");
      assertThat(unknownApiResponse.status()).isEqualTo(404);
    }
  }

  private void logInAsDemoUser(Page page) {
    page.navigate(LOGIN_URL);
    page.locator("#username").fill(DEMO_EMAIL);
    page.locator("#password").fill(DEMO_PASSWORD);
    page.locator("button[type=submit]").click();
    page.waitForURL(ROOT_URL);
  }

  private void assertBuiltAssetsResolve(Page page) {
    List<String> assetUrls = builtAssetUrls(page);
    assertThat(assetUrls).isNotEmpty();
    for (String assetUrl : assetUrls) {
      APIResponse assetResponse = context.request().get(assetUrl);
      assertThat(assetResponse.status()).isEqualTo(200);
      assertThat(assetResponse.body()).isNotEmpty();
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> builtAssetUrls(Page page) {
    Object hrefs =
        page.evaluate(
            "Array.from(document.querySelectorAll("
                + "'script[type=module][src], link[rel=stylesheet][href]'))"
                + ".map(el => el.getAttribute('src') || el.getAttribute('href'))");
    return ((List<Object>) hrefs)
        .stream().map(href -> ApplicationUnderTest.BASE_URI + href).toList();
  }
}
