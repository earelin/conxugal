package gal.conxugal.application.rest.admin.contratosmenores;

import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
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
 * The ineligible-Órgano refusal, distinguishable from the guard being held without reading prose:
 * this one repeats until the catalogue or the mark changes, and an administrator told only that an
 * import is running would wait for one that is never going to start.
 *
 * <p>Which half of eligibility the Órgano fails is not reported, because what to do about it is
 * the same either way.
 */
@Singleton
class OrganoNotEligibleForImportExceptionHandler
    implements ExceptionHandler<OrganoNotEligibleForImportException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:organo-not-eligible");

  private final ThrowableProblemHandler throwableProblemHandler;

  OrganoNotEligibleForImportExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(
      HttpRequest request, OrganoNotEligibleForImportException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Órgano Not Eligible")
            .withStatus(new HttpStatusType(HttpStatus.CONFLICT))
            .withDetail("Órgano %s is not active in the catalogue and marked for import"
                .formatted(exception.getOrganoId()))
            .build());
  }
}
