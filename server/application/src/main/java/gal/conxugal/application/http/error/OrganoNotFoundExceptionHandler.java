package gal.conxugal.application.http.error;

import gal.conxugal.domain.organo.OrganoNotFoundException;
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
 * The one rendering of an unknown Órgano, for every path that can name one.
 *
 * <p><b>It lives here rather than beside a controller because more than one slice throws it.</b>
 * The administration writes under {@code rest/admin/organos} raise it, and so does the member read
 * under {@code rest/organos}; a handler co-located with either would be owned by a slice that is
 * not the only caller. Nothing about the container required the move — Micronaut resolves an
 * {@link ExceptionHandler} by the exception type it is declared over, not by the package it sits
 * in, so the handler applied across packages while it was still filed under {@code admin}. What
 * the move buys is that where it lives now says what it is.
 *
 * <p>There is deliberately one problem type for this condition and not one per caller: a client
 * that learns {@code organo-not-found} learns it once.
 */
@Singleton
class OrganoNotFoundExceptionHandler
    implements ExceptionHandler<OrganoNotFoundException, HttpResponse<?>> {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:organo-not-found");

  private final ThrowableProblemHandler throwableProblemHandler;

  OrganoNotFoundExceptionHandler(ThrowableProblemHandler throwableProblemHandler) {
    this.throwableProblemHandler = throwableProblemHandler;
  }

  @Override
  public HttpResponse<?> handle(HttpRequest request, OrganoNotFoundException exception) {
    return throwableProblemHandler.handle(
        request,
        Problem.builder()
            .withType(TYPE)
            .withTitle("Órgano Not Found")
            .withStatus(new HttpStatusType(HttpStatus.NOT_FOUND))
            .withDetail("No Órgano exists with id: %s".formatted(exception.getOrganoId()))
            .build());
  }
}
