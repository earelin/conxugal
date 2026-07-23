package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.DuplicateEmailException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;
import java.net.URI;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

@Singleton
class DuplicateEmailExceptionHandler
    implements ExceptionHandler<DuplicateEmailException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:duplicate-email");

  private final ThrowableProblemHandler throwableProblemHandler;

  DuplicateEmailExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, DuplicateEmailException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Conflict")
            .withStatus(Status.CONFLICT)
            .withDetail("An account with email " + exception.getEmail() + " already exists.")
            .build());
  }
}
