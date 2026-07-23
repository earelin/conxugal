package gal.conxugal.application.rest.admin.users;

import java.net.URI;
import java.util.UUID;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** The {@code 404} response for {@link gal.conxugal.domain.user.UserNotFoundException}. */
class UserNotFoundProblem extends AbstractThrowableProblem {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:user-not-found");

  UserNotFoundProblem(UUID userId) {
    super(TYPE, "Not Found", Status.NOT_FOUND, "No account exists with id: " + userId);
  }
}
