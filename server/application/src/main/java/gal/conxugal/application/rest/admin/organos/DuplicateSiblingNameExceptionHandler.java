package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.taxonomia.DuplicateSiblingNameException;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.HttpStatusType;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;
import java.net.URI;
import org.zalando.problem.Problem;

@Singleton
class DuplicateSiblingNameExceptionHandler
    implements ExceptionHandler<DuplicateSiblingNameException, HttpResponse<?>> {

  private static final URI TYPE =
      URI.create("urn:conxugal:problem-type:duplicate-sibling-name");

  private final ThrowableProblemHandler throwableProblemHandler;

  DuplicateSiblingNameExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, DuplicateSiblingNameException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Duplicate Sibling Name")
            .withStatus(new HttpStatusType(HttpStatus.CONFLICT))
            .withDetail(detailOf(exception.getName(), exception.getParentId()))
            .build());
  }

  /**
   * Deliberately re-derived rather than taken from the exception's own message: that message
   * is for logs and may be reworded freely, while this one is published in the problem body
   * and is part of the API. They read alike today, and neither is obliged to follow the
   * other. The same holds for every handler in this package.
   */
  private static String detailOf(String name, @Nullable TermoId parentId) {
    return parentId == null
        ? "A root term named %s already exists".formatted(name)
        : "A term named %s already exists under parent %s".formatted(name, parentId);
  }
}
