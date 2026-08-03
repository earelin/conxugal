package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.UserNotFoundException;
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
class UserNotFoundExceptionHandler
    implements ExceptionHandler<UserNotFoundException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:user-not-found");

  private final ThrowableProblemHandler throwableProblemHandler;

  UserNotFoundExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, UserNotFoundException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Not Found")
            .withStatus(new HttpStatusType(HttpStatus.NOT_FOUND))
            .withDetail("No account exists with id: " + exception.getUserId())
            .build());
  }
}
