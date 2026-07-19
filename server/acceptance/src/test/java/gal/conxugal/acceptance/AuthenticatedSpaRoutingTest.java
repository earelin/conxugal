package gal.conxugal.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
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
  private Page page;

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
  void logInAsDemoUser() {
    context = browser.newContext();
    page = context.newPage();
    page.navigate(LOGIN_URL);
    page.locator("#username").fill(DEMO_EMAIL);
    page.locator("#password").fill(DEMO_PASSWORD);
    page.locator("button[type=submit]").click();
    page.waitForURL(ROOT_URL);
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  @Test
  void root_serves_the_spa_shell_with_its_built_assets() {
    Response rootResponse = page.navigate(ROOT_URL);

    assertThat(rootResponse.status()).isEqualTo(200);
    assertThat(rootResponse.headerValue("content-type")).contains("text/html");
    PlaywrightAssertions.assertThat(page.getByText("Benvido/a a conxugal")).isVisible();
    assertBuiltAssetsResolve();
  }

  @Test
  void known_client_side_route_falls_back_to_the_spa_shell() {
    Response acercaResponse = page.navigate(ApplicationUnderTest.BASE_URI + "/acerca");

    assertThat(acercaResponse.status()).isEqualTo(200);
    assertThat(acercaResponse.headerValue("content-type")).contains("text/html");
    PlaywrightAssertions.assertThat(page.getByText("Acerca do proxecto")).isVisible();
  }

  @Test
  void unknown_route_falls_back_to_the_spa_shell_and_renders_its_not_found_page() {
    Response unknownRouteResponse =
        page.navigate(ApplicationUnderTest.BASE_URI + "/rota-que-non-existe");

    assertThat(unknownRouteResponse.status()).isEqualTo(200);
    assertThat(unknownRouteResponse.headerValue("content-type")).contains("text/html");
    PlaywrightAssertions.assertThat(page.getByText("Páxina non atopada")).isVisible();
  }

  @Test
  void unknown_api_route_returns_not_found_never_the_spa_shell() {
    APIResponse unknownApiResponse =
        context.request().get(ApplicationUnderTest.BASE_URI + "/api/rota-que-non-existe");

    assertThat(unknownApiResponse.status()).isEqualTo(404);
  }

  private void assertBuiltAssetsResolve() {
    List<String> assetUrls = builtAssetUrls();
    assertThat(assetUrls).isNotEmpty();
    for (String assetUrl : assetUrls) {
      APIResponse assetResponse = context.request().get(assetUrl);
      assertThat(assetResponse.status()).isEqualTo(200);
      assertThat(assetResponse.body()).isNotEmpty();
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> builtAssetUrls() {
    Object hrefs =
        page.evaluate(
            "Array.from(document.querySelectorAll("
                + "'script[type=module][src], link[rel=stylesheet][href]'))"
                + ".map(el => el.getAttribute('src') || el.getAttribute('href'))");
    return ((List<Object>) hrefs)
        .stream().map(href -> ApplicationUnderTest.BASE_URI + href).toList();
  }
}
