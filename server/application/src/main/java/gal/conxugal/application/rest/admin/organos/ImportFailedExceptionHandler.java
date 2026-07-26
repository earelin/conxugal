package gal.conxugal.application.rest.admin.organos;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;
import java.net.URI;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

@Singleton
class ImportFailedExceptionHandler
    implements ExceptionHandler<ImportFailedException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:organo-import-failed");

  private final ThrowableProblemHandler throwableProblemHandler;

  ImportFailedExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, ImportFailedException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Import Failed")
            .withStatus(Status.INTERNAL_SERVER_ERROR)
            .withDetail(
                "The Órganos import failed: the source was unreachable or returned an "
                    + "unusable response.")
            .build());
  }
}
