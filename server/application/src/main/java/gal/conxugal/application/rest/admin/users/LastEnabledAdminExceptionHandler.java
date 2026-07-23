package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.LastEnabledAdminException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;
import java.net.URI;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

@Singleton
class LastEnabledAdminExceptionHandler
    implements ExceptionHandler<LastEnabledAdminException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:last-enabled-admin");

  private final ThrowableProblemHandler throwableProblemHandler;

  LastEnabledAdminExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, LastEnabledAdminException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Conflict")
            .withStatus(Status.CONFLICT)
            .withDetail(
                "Cannot disable the only remaining enabled ADMIN account: "
                    + exception.getUserId())
            .build());
  }
}
