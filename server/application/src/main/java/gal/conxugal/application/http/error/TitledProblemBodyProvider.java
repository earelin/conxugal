package gal.conxugal.application.http.error;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.problem.ProblemJsonErrorResponseBodyProvider;
import io.micronaut.problem.conf.ProblemConfiguration;
import jakarta.inject.Singleton;
import org.zalando.problem.Problem;
import org.zalando.problem.ProblemBuilder;
import org.zalando.problem.ThrowableProblem;

/**
 * Gives every refusal the {@code title} the {@code Error} schema requires.
 *
 * <p>Micronaut builds the problem document for the failures it raises itself — an
 * unauthenticated caller, a forbidden one, a request it could not read — and titles it only
 * when the failure carried an error to take a title from. The security filter carries none, so
 * those responses go out as {@code {"type":"about:blank","status":401}}: a body missing a
 * property the contract marks required. Refusals the application raises itself are untouched,
 * since each throws a problem that already names itself.
 */
@Singleton
@Replaces(ProblemJsonErrorResponseBodyProvider.class)
public class TitledProblemBodyProvider extends ProblemJsonErrorResponseBodyProvider {

  public TitledProblemBodyProvider(ProblemConfiguration config) {
    super(config);
  }

  @Override
  protected ThrowableProblem defaultProblem(ErrorContext errorContext, HttpStatus httpStatus) {
    ThrowableProblem problem = super.defaultProblem(errorContext, httpStatus);
    if (problem.getTitle() != null) {
      return problem;
    }
    ProblemBuilder titled = Problem.builder()
        .withType(problem.getType())
        .withStatus(problem.getStatus())
        .withTitle(httpStatus.getReason())
        .withDetail(problem.getDetail())
        .withInstance(problem.getInstance());
    problem.getParameters().forEach(titled::with);
    return titled.build();
  }
}
