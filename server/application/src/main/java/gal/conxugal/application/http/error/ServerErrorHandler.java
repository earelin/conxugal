package gal.conxugal.application.http.error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ServerErrorHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

  private static final Logger LOG = LoggerFactory.getLogger(ServerErrorHandler.class);
  private static final String API_PATH_PREFIX = "/api/";
  private static final MediaType APPLICATION_PROBLEM_JSON_TYPE =
      new MediaType("application/problem+json");
  private static final String INTERNAL_SERVER_ERROR_PROBLEM_TYPE =
      "urn:conxugal:problem-type:internal-server-error";

  @Override
  public HttpResponse<?> handle(HttpRequest request, Throwable e) {
    LOG.error("Unhandled exception handling {} {}", request.getMethod(), request.getPath(), e);

    if (request.getPath().startsWith(API_PATH_PREFIX)) {
      return HttpResponse.serverError(problemDetailOf(request))
          .contentType(APPLICATION_PROBLEM_JSON_TYPE);
    }

    Map<String, Object> model = Map.of("retryHref", request.getPath());
    return HttpResponse.serverError(new ModelAndView<>("server-error", model))
        .contentType(MediaType.TEXT_HTML_TYPE);
  }

  // RFC 9457 Problem Details body — deliberately excludes the exception message/stack trace.
  private Map<String, Object> problemDetailOf(HttpRequest<?> request) {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", INTERNAL_SERVER_ERROR_PROBLEM_TYPE);
    problem.put("title", "Internal Server Error");
    problem.put("status", HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    problem.put("detail", "An unexpected error occurred while processing the request.");
    problem.put("instance", request.getPath());
    return problem;
  }
}
