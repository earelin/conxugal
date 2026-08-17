package gal.conxugal.application.rest.request;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.problem.HttpStatusType;
import java.util.Comparator;
import java.util.Set;
import java.util.regex.Pattern;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

/**
 * What a REST operation refuses about its own request, before any use case runs.
 *
 * <p>Shared rather than per-controller because both rules here are about the request rather than
 * about any one resource, and an operation refusing differently from its neighbours is a
 * difference a caller experiences as inconsistency rather than as a design — the same reason the
 * paged envelope is declared once.
 */
public final class Refusals {

  private static final Pattern CONTROL_OR_SPACE = Pattern.compile("[\\p{Cntrl}\\s]+");
  private static final int MAX_ECHOED_NAME = 40;

  private Refusals() {
  }

  /**
   * A refusal that says what it is about. Thrown as a problem rather than as a status, because the
   * framework's status handler drops the message: a caller would be told the request was refused
   * and left to work out which of the operation's rules it broke.
   *
   * <p>It carries no problem type of its own. {@code about:blank} is what the shared {@code 400}
   * response in the contract already declares, and a type per validation rule would be a
   * vocabulary clients would have to learn to obtain what the detail already says.
   */
  public static ThrowableProblem refused(String why) {
    return Problem.builder()
        .withTitle("Bad Request")
        .withStatus(new HttpStatusType(HttpStatus.BAD_REQUEST))
        .withDetail(why)
        .build();
  }

  /**
   * Refuses a query parameter the operation does not have, which is the same rule as refusing a
   * value it does not offer, applied one level up. A misspelt parameter is otherwise the quietest
   * failure an operation can have: it is dropped, the default is applied, and the answer looks
   * exactly like the one that was asked for — on a read whose response states none of its
   * selection back, the URL being what carries it.
   *
   * <p><b>It names one, and names it briefly.</b> A parameter name is caller-controlled text of
   * whatever length and characters a URI admits, percent-decoded by the time it is read here. One
   * short name is enough for a caller to find its mistake, and it bounds how much of a stranger's
   * input this server repeats back and writes to whatever reads the problem.
   */
  public static void refuseUnknownParameters(HttpRequest<?> request, Set<String> accepted) {
    request.getParameters()
        .names()
        .stream()
        .filter(name -> !accepted.contains(name))
        .min(Comparator.naturalOrder())
        .ifPresent(name -> {
          throw refused("no such query parameter: %s".formatted(oneShortLine(name)));
        });
  }

  /**
   * One line, and a short one. A percent-encoded newline is a name a caller can send, and the
   * problem document's detail is declared as a single line — so a refusal that echoed one verbatim
   * would answer with a body its own contract rejects.
   */
  private static String oneShortLine(String name) {
    String flattened = CONTROL_OR_SPACE.matcher(name).replaceAll(" ");
    return flattened.length() <= MAX_ECHOED_NAME
        ? flattened
        : flattened.substring(0, MAX_ECHOED_NAME) + "…";
  }
}
