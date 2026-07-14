package gal.conxugal.application.http.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.csrf.repository.CsrfLoginCookieProvider;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.views.ModelAndView;
import java.util.Map;

@Controller
public class ForbiddenController {

  private final CsrfLoginCookieProvider csrfCookieProvider;

  public ForbiddenController(CsrfLoginCookieProvider csrfCookieProvider) {
    this.csrfCookieProvider = csrfCookieProvider;
  }

  @Secured(SecurityRule.IS_AUTHENTICATED)
  @Produces(MediaType.TEXT_HTML)
  @Get("/forbidden")
  HttpResponse<ModelAndView<Map<String, Object>>> forbidden(HttpRequest<?> request) {
    Cookie csrfCookie = csrfCookieProvider.provideCookie(request);
    Map<String, Object> model = Map.of("csrfToken", csrfCookie.getValue());
    return HttpResponse.ok(new ModelAndView<>("forbidden", model)).cookie(csrfCookie);
  }
}
