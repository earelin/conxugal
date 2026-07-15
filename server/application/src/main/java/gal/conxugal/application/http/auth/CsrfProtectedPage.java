package gal.conxugal.application.http.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.csrf.repository.CsrfLoginCookieProvider;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
class CsrfProtectedPage {

  private final CsrfLoginCookieProvider csrfCookieProvider;

  CsrfProtectedPage(CsrfLoginCookieProvider csrfCookieProvider) {
    this.csrfCookieProvider = csrfCookieProvider;
  }

  HttpResponse<ModelAndView<Map<String, Object>>> render(
      HttpRequest<?> request, String view, Map<String, Object> model) {
    Cookie csrfCookie = csrfCookieProvider.provideCookie(request);
    Map<String, Object> fullModel = new LinkedHashMap<>(model);
    fullModel.put("csrfToken", csrfCookie.getValue());
    return HttpResponse.ok(new ModelAndView<>(view, fullModel)).cookie(csrfCookie);
  }
}
