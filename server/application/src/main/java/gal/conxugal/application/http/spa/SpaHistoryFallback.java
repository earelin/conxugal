package gal.conxugal.application.http.spa;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.types.files.StreamedFile;

@Controller
public final class SpaHistoryFallback {

  private static final String API_PATH_PREFIX = "/api/";
  private static final String INDEX_HTML = "classpath:public/index.html";

  private final ResourceResolver resourceResolver;

  public SpaHistoryFallback(ResourceResolver resourceResolver) {
    this.resourceResolver = resourceResolver;
  }

  @Error(global = true, status = HttpStatus.NOT_FOUND)
  @Produces(MediaType.TEXT_HTML)
  public HttpResponse<?> handleUnmatched(HttpRequest<?> request) {
    if (!isSpaNavigation(request)) {
      return HttpResponse.notFound();
    }
    return resourceResolver
        .getResource(INDEX_HTML)
        .<HttpResponse<?>>map(url -> HttpResponse.ok(new StreamedFile(url)))
        .orElseGet(HttpResponse::notFound);
  }

  private static boolean isSpaNavigation(HttpRequest<?> request) {
    return request.getMethod() == HttpMethod.GET
        && !request.getPath().startsWith(API_PATH_PREFIX);
  }
}
