package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.UserNotFoundException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;

@Singleton
class UserNotFoundExceptionHandler
    implements ExceptionHandler<UserNotFoundException, HttpResponse<?>> {

  private final ThrowableProblemHandler throwableProblemHandler;

  UserNotFoundExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, UserNotFoundException exception) {
    return throwableProblemHandler.handle(
        request, new UserNotFoundProblem(exception.getUserId()));
  }
}
