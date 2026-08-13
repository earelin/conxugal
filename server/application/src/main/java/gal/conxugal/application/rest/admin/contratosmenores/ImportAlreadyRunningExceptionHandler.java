package gal.conxugal.application.rest.admin.contratosmenores;

import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.problem.HttpStatusType;
import io.micronaut.problem.ThrowableProblemHandler;
import jakarta.inject.Singleton;
import java.net.URI;
import org.zalando.problem.Problem;

/**
 * The guard refusal, as its own problem type. A caller that can only read the {@code 409} cannot
 * tell this from an ineligible Órgano — one resolves itself when the running import finishes, the
 * other never does — so the type is what carries the distinction rather than the prose.
 *
 * <p>It names neither the running import nor its run: the guard admits one import at a time across
 * the whole system, so what refused this one may be another Órgano's, the catalogue's, or the same
 * trigger arriving twice.
 */
@Singleton
class ImportAlreadyRunningExceptionHandler
    implements ExceptionHandler<ImportAlreadyRunningException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:import-already-running");

  private final ThrowableProblemHandler throwableProblemHandler;

  ImportAlreadyRunningExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, ImportAlreadyRunningException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Import Already Running")
            .withStatus(new HttpStatusType(HttpStatus.CONFLICT))
            .withDetail("An import is already running, so no further import was started")
            .build());
  }
}
