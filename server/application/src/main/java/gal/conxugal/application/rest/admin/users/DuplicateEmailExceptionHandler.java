package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.DuplicateEmailException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;

@Singleton
class DuplicateEmailExceptionHandler
    implements ExceptionHandler<DuplicateEmailException, HttpResponse<?>> {

  private final ThrowableProblemHandler throwableProblemHandler;

  DuplicateEmailExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, DuplicateEmailException exception) {
    return throwableProblemHandler.handle(
        request, new DuplicateEmailProblem(exception.getEmail()));
  }
}
