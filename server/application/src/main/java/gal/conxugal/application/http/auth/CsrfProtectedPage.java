package gal.conxugal.application.http.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.security.csrf.repository.CookieCsrfTokenRepository;
import io.micronaut.security.csrf.repository.CsrfLoginCookieProvider;
import io.micronaut.security.csrf.validator.CsrfTokenValidator;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
class CsrfProtectedPage {

  private final CsrfLoginCookieProvider csrfCookieProvider;
  private final CookieCsrfTokenRepository csrfCookieRepository;
  private final CsrfTokenValidator<HttpRequest<?>> csrfTokenValidator;
  private final CsrfConfiguration csrfConfiguration;

  CsrfProtectedPage(
      CsrfLoginCookieProvider csrfCookieProvider,
      CookieCsrfTokenRepository csrfCookieRepository,
      CsrfTokenValidator<HttpRequest<?>> csrfTokenValidator,
      CsrfConfiguration csrfConfiguration) {
    this.csrfCookieProvider = csrfCookieProvider;
    this.csrfCookieRepository = csrfCookieRepository;
    this.csrfTokenValidator = csrfTokenValidator;
    this.csrfConfiguration = csrfConfiguration;
  }

  HttpResponse<ModelAndView<Map<String, Object>>> render(
      HttpRequest<?> request, String view, Map<String, Object> model) {
    Cookie csrfCookie = csrfCookie(request);
    Map<String, Object> fullModel = new LinkedHashMap<>(model);
    fullModel.put("csrfToken", csrfCookie.getValue());
    return HttpResponse.ok(new ModelAndView<>(view, fullModel)).cookie(csrfCookie);
  }

  // The CSRF cookie and the token embedded in the form must be identical for the
  // double-submit check to pass. Minting a fresh token on every render breaks that
  // whenever the page is fetched more than once before submitting (e.g. an unauthorized
  // redirect to /login followed by the browser loading it): the last response's cookie no
  // longer matches the form the user actually submits. Reuse an already-valid token so
  // repeated loads render a stable value, re-issuing the cookie exactly as the provider
  // does so its max-age keeps sliding.
  private Cookie csrfCookie(HttpRequest<?> request) {
    return csrfCookieRepository.findCsrfToken(request)
        .filter(token -> csrfTokenValidator.validateCsrfToken(request, token))
        .map(token -> Cookie.of(csrfConfiguration.getCookieName(), token)
            .configure(csrfConfiguration, request.isSecure()))
        .orElseGet(() -> csrfCookieProvider.provideCookie(request));
  }
}
