package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.LastEnabledAdminException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;

@Singleton
class LastEnabledAdminExceptionHandler
    implements ExceptionHandler<LastEnabledAdminException, HttpResponse<?>> {

  private final ThrowableProblemHandler throwableProblemHandler;

  LastEnabledAdminExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, LastEnabledAdminException exception) {
    return throwableProblemHandler.handle(
        request, new LastEnabledAdminProblem(exception.getUserId()));
  }
}
