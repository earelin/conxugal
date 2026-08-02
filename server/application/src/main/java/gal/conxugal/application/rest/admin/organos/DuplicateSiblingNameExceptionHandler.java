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
            .withTitle("Conflict")
            .withStatus(new HttpStatusType(HttpStatus.CONFLICT))
            .withDetail(detailOf(exception.getName(), exception.getParentId()))
            .build());
  }

  private static String detailOf(String name, @Nullable TermoId parentId) {
    return parentId == null
        ? "A root term named " + name + " already exists"
        : "A term named " + name + " already exists under parent " + parentId;
  }
}
