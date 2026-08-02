package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.taxonomia.TermoNotFoundException;
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
class TermoNotFoundExceptionHandler
    implements ExceptionHandler<TermoNotFoundException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:termo-not-found");

  private final ThrowableProblemHandler throwableProblemHandler;

  TermoNotFoundExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, TermoNotFoundException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Term Not Found")
            .withStatus(new HttpStatusType(HttpStatus.NOT_FOUND))
            .withDetail("No term exists with id: %s".formatted(exception.getTermoId()))
            .build());
  }
}
