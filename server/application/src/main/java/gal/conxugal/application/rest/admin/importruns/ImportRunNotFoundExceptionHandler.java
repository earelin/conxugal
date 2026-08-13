package gal.conxugal.application.rest.admin.importruns;

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
class ImportRunNotFoundExceptionHandler
    implements ExceptionHandler<ImportRunNotFoundException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:import-run-not-found");

  private final ThrowableProblemHandler throwableProblemHandler;

  ImportRunNotFoundExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, ImportRunNotFoundException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Import Run Not Found")
            .withStatus(new HttpStatusType(HttpStatus.NOT_FOUND))
            .withDetail("No import run exists with id: %s".formatted(exception.getRunId()))
            .build());
  }
}
