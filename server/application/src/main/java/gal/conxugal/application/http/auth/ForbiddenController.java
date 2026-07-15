package gal.conxugal.application.http.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.views.ModelAndView;
import java.util.Map;

@Controller
public class ForbiddenController {

  private final CsrfProtectedPage csrfProtectedPage;

  public ForbiddenController(CsrfProtectedPage csrfProtectedPage) {
    this.csrfProtectedPage = csrfProtectedPage;
  }

  @Secured(SecurityRule.IS_AUTHENTICATED)
  @Produces(MediaType.TEXT_HTML)
  @Get("/forbidden")
  HttpResponse<ModelAndView<Map<String, Object>>> forbidden(HttpRequest<?> request) {
    return csrfProtectedPage.render(request, "forbidden", Map.of());
  }
}
