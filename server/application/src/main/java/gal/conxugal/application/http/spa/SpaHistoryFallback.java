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
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public final class SpaHistoryFallback {

  private static final String API_PATH_PREFIX = "/api/";
  private static final String ASSETS_PATH_PREFIX = "/assets/";
  private static final String INDEX_HTML = "classpath:public/index.html";
  private static final MediaType APPLICATION_PROBLEM_JSON_TYPE =
      new MediaType("application/problem+json");
  private static final String NOT_FOUND_PROBLEM_TYPE = "urn:conxugal:problem-type:not-found";

  private final ResourceResolver resourceResolver;

  public SpaHistoryFallback(ResourceResolver resourceResolver) {
    this.resourceResolver = resourceResolver;
  }

  @Error(global = true, status = HttpStatus.NOT_FOUND)
  @Produces(MediaType.ALL)
  public HttpResponse<?> handleUnmatched(HttpRequest<?> request) {
    if (isSpaNavigation(request)) {
      return serveAppShell();
    }
    if (request.getPath().startsWith(API_PATH_PREFIX)) {
      return HttpResponse.notFound(problemDetailOf(request))
          .contentType(APPLICATION_PROBLEM_JSON_TYPE);
    }
    return HttpResponse.notFound();
  }

  private HttpResponse<?> serveAppShell() {
    return resourceResolver
        .getResource(INDEX_HTML)
        .<HttpResponse<?>>map(url -> HttpResponse.ok(new StreamedFile(url)))
        .orElseGet(HttpResponse::notFound);
  }

  // A GET outside /api/ that matched no static asset is a client-side route: serve the shell
  // so React Router resolves it. Asset-shaped requests (under /assets/, or carrying a file
  // extension) are excluded — a missed asset is a genuine 404; serving the shell for one would
  // mask the miss (e.g. a stale hashed chunk after a redeploy) and expose the shell to
  // anonymous callers of the public /assets/ namespace.
  private static boolean isSpaNavigation(HttpRequest<?> request) {
    String path = request.getPath();
    return request.getMethod() == HttpMethod.GET
        && !path.startsWith(API_PATH_PREFIX)
        && !isStaticAssetPath(path);
  }

  private static boolean isStaticAssetPath(String path) {
    return path.startsWith(ASSETS_PATH_PREFIX)
        || path.substring(path.lastIndexOf('/') + 1).indexOf('.') >= 0;
  }

  // RFC 9457 Problem Details, matching the API-error shape ServerErrorHandler establishes.
  private static Map<String, Object> problemDetailOf(HttpRequest<?> request) {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", NOT_FOUND_PROBLEM_TYPE);
    problem.put("title", "Not Found");
    problem.put("status", HttpStatus.NOT_FOUND.getCode());
    problem.put("detail", "The requested resource was not found.");
    problem.put("instance", request.getPath());
    return problem;
  }
}
