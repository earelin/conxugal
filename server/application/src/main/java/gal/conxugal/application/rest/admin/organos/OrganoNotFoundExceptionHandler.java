package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.OrganoNotFoundException;
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
class OrganoNotFoundExceptionHandler
    implements ExceptionHandler<OrganoNotFoundException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:organo-not-found");

  private final ThrowableProblemHandler throwableProblemHandler;

  OrganoNotFoundExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, OrganoNotFoundException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Órgano Not Found")
            .withStatus(new HttpStatusType(HttpStatus.NOT_FOUND))
            .withDetail("No Órgano exists with id: %s".formatted(exception.getOrganoId()))
            .build());
  }
}
