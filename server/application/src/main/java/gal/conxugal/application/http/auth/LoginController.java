package gal.conxugal.application.http.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import java.net.URI;
import java.util.Map;

@Controller
public class LoginController {

  private final CsrfProtectedPage csrfProtectedPage;

  public LoginController(CsrfProtectedPage csrfProtectedPage) {
    this.csrfProtectedPage = csrfProtectedPage;
  }

  @Secured(SecurityRule.IS_ANONYMOUS)
  @Produces(MediaType.TEXT_HTML)
  @Get("/login")
  HttpResponse<?> login(
      HttpRequest<?> request,
      @Nullable Authentication authentication,
      @Nullable @QueryValue("error") String error) {
    if (authentication != null) {
      return HttpResponse.seeOther(URI.create("/"));
    }
    return csrfProtectedPage.render(request, "login", Map.of("error", error != null));
  }
}
