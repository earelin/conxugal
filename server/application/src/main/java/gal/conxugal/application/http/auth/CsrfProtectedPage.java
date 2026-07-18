package gal.conxugal.application.http.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.csrf.repository.CookieCsrfTokenRepository;
import io.micronaut.security.csrf.repository.CsrfLoginCookieProvider;
import io.micronaut.security.csrf.validator.CsrfTokenValidator;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Singleton
class CsrfProtectedPage {

  private final CsrfLoginCookieProvider csrfCookieProvider;
  private final CookieCsrfTokenRepository csrfCookieRepository;
  private final CsrfTokenValidator<HttpRequest<?>> csrfTokenValidator;

  CsrfProtectedPage(
      CsrfLoginCookieProvider csrfCookieProvider,
      CookieCsrfTokenRepository csrfCookieRepository,
      CsrfTokenValidator<HttpRequest<?>> csrfTokenValidator) {
    this.csrfCookieProvider = csrfCookieProvider;
    this.csrfCookieRepository = csrfCookieRepository;
    this.csrfTokenValidator = csrfTokenValidator;
  }

  HttpResponse<ModelAndView<Map<String, Object>>> render(
      HttpRequest<?> request, String view, Map<String, Object> model) {
    // The CSRF cookie and the token embedded in the form must be identical for the
    // double-submit check to pass. Minting a fresh token on every render breaks that
    // whenever the page is fetched more than once before submitting (e.g. an
    // unauthorized redirect to /login followed by the browser loading it): the last
    // response's cookie no longer matches the form the user actually submits. Reuse an
    // already-valid cookie so repeated loads render a stable, matching token.
    Optional<String> existingToken = csrfCookieRepository.findCsrfToken(request)
        .filter(token -> csrfTokenValidator.validateCsrfToken(request, token));
    if (existingToken.isPresent()) {
      Map<String, Object> fullModel = new LinkedHashMap<>(model);
      fullModel.put("csrfToken", existingToken.get());
      return HttpResponse.ok(new ModelAndView<>(view, fullModel));
    }

    Cookie csrfCookie = csrfCookieProvider.provideCookie(request);
    Map<String, Object> fullModel = new LinkedHashMap<>(model);
    fullModel.put("csrfToken", csrfCookie.getValue());
    return HttpResponse.ok(new ModelAndView<>(view, fullModel)).cookie(csrfCookie);
  }
}
