package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.taxonomia.TermoCycleException;
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
class TermoCycleExceptionHandler
    implements ExceptionHandler<TermoCycleException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:termo-cycle");

  private final ThrowableProblemHandler throwableProblemHandler;

  TermoCycleExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, TermoCycleException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Conflict")
            .withStatus(new HttpStatusType(HttpStatus.CONFLICT))
            .withDetail("Cannot move term " + exception.getTermoId()
                + " under itself or one of its descendants: " + exception.getTargetParentId())
            .build());
  }
}
