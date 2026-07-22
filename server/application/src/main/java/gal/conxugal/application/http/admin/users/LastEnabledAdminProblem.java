package gal.conxugal.application.http.admin.users;

import java.net.URI;
import java.util.UUID;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** The {@code 409} response for {@link gal.conxugal.domain.user.LastEnabledAdminException}. */
class LastEnabledAdminProblem extends AbstractThrowableProblem {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:last-enabled-admin");

  LastEnabledAdminProblem(UUID userId) {
    super(TYPE, "Conflict", Status.CONFLICT,
        "Cannot disable the only remaining enabled ADMIN account: " + userId);
  }
}
