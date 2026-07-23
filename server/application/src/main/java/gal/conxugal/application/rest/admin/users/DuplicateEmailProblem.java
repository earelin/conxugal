package gal.conxugal.application.rest.admin.users;

import java.net.URI;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** The {@code 409} response for {@link gal.conxugal.domain.user.DuplicateEmailException}. */
class DuplicateEmailProblem extends AbstractThrowableProblem {

  private static final URI TYPE = URI.create("urn:conxugal:problem-type:duplicate-email");

  DuplicateEmailProblem(String email) {
    super(TYPE, "Conflict", Status.CONFLICT, "An account with email " + email + " already exists.");
  }
}
