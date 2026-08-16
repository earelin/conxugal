package gal.conxugal.application.rest.contratosmenores;

import gal.conxugal.application.rest.paging.PagedResponse;
import gal.conxugal.domain.contrato.ListContratosMenores;
import gal.conxugal.domain.contrato.SortKey;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import gal.conxugal.domain.contrato.YearSelection;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.problem.HttpStatusType;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

/**
 * One Órgano's contratos menores, a year at a time. Open to any authenticated caller — the read is
 * granted to {@code USER} and {@code ADMIN} alike, it modifies nothing, and requiring a session at
 * all is the mitigation the whole surface rests on.
 *
 * <p><b>This is the only place the two paging vocabularies meet.</b> Inbound, the contract's
 * parameters become a year, a sort key, a direction and a <b>0-based, unsorted</b> pageable;
 * outbound, the store's page becomes the 1-based envelope. Nothing above this class sees a
 * pageable and nothing below it sees the envelope, so an off-by-one has exactly one place to be.
 *
 * <p><b>Nothing binds a pageable from the request, and that is a security invariant rather than a
 * preference.</b> The framework's binder accepts any property name and silently degrades an
 * unrecognised direction to ascending; the browse statement is native SQL and appends a property
 * name to its {@code ORDER BY} verbatim and unescaped. So the ordering is carried by two enums the
 * whole way down, and the parameter that named them is refused here — before the domain call —
 * when it names anything else. The mechanism by which a caller's text could reach a query is
 * absent rather than guarded.
 *
 * <p><b>Every refusal is a refusal.</b> A missing or malformed year, an ordering outside the four
 * offered, a page below one, a size outside 1–100: each is a {@code 400}, none is corrected to
 * something answerable. An answer that quietly differed from the request that produced it would be
 * worse than an error, because nothing on the wire would say so — the ordering is not echoed back,
 * the URL being what carries it.
 *
 * <p>A page beyond the last is <b>not</b> among them: it is a legitimate question about a real
 * selection, and it is answered with an empty page carrying that selection's true totals, which is
 * what lets a client clamp rather than guess.
 */
@Controller("/api/organo")
@Secured(SecurityRule.IS_AUTHENTICATED)
class ContratosMenoresController {

  private static final String DEFAULT_SORT = "publicationDate,desc";
  private static final String ASCENDING = "asc";
  private static final String DESCENDING = "desc";
  private static final Set<String> ACCEPTED_PARAMETERS = Set.of("year", "sort", "page", "size");
  private static final Pattern CONTROL_OR_SPACE = Pattern.compile("[\\p{Cntrl}\\s]+");
  private static final int MAX_ECHOED_NAME = 40;
  private static final int MIN_PAGE = 1;
  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 100;

  private final ListContratosMenores listContratosMenores;
  private final ContratosMenoresPublicationConfiguration publication;

  ContratosMenoresController(
      ListContratosMenores listContratosMenores,
      ContratosMenoresPublicationConfiguration publication) {
    this.listContratosMenores = listContratosMenores;
    this.publication = publication;
  }

  @Get("/{id}/contratos-menores")
  PagedResponse<ContratoMenorResponse> list(
      HttpRequest<?> request,
      @PathVariable UUID id,
      @QueryValue @Nullable String year,
      @QueryValue(defaultValue = DEFAULT_SORT) String sort,
      @QueryValue(defaultValue = "1") int page,
      @QueryValue(defaultValue = "50") int size) {
    refuseUnknownParameters(request);
    YearSelection selection = yearOf(year);
    Ordering ordering = orderingOf(sort);
    Pageable pageable = pageableOf(page, size);

    Page<VisibleContratoMenor> answered =
        listContratosMenores.list(
            new OrganoId(id), selection, ordering.key(), ordering.direction(), pageable);

    return PagedResponse.of(answered, row -> ContratoMenorResponse.of(row, publication));
  }

  /**
   * Refuses a query parameter this operation does not have, which is the same rule as refusing an
   * ordering it does not offer, applied one level up. A misspelt {@code sort} is otherwise the
   * quietest failure this endpoint could have: the parameter is dropped, the default ordering is
   * applied, and the response — which states no ordering, the URL being what carries it — looks
   * exactly like the one that was asked for.
   *
   * <p><b>It names one, and names it briefly.</b> This is the only refusal here whose detail is
   * not a constant, and a parameter name is caller-controlled text of whatever length and
   * characters a URI admits — percent-decoded by the time it is read here. One short name is
   * enough for a caller to find its mistake, and it bounds how much of a stranger's input this
   * server repeats back and writes to whatever reads the problem.
   */
  private static void refuseUnknownParameters(HttpRequest<?> request) {
    request.getParameters()
        .names()
        .stream()
        .filter(name -> !ACCEPTED_PARAMETERS.contains(name))
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

  private static YearSelection yearOf(@Nullable String year) {
    return YearSelection.parse(year)
        .orElseThrow(
            () -> refused(
                "year is required and must be a four-digit year: there is no all-years list"));
  }

  /**
   * The two enums the ordering travels as, or a refusal — never a fallback to the default. Both
   * halves are parsed by the domain's own {@code parse}, so the set of orderings this endpoint
   * offers is the set the domain has rather than a second list kept in step with it by hand.
   */
  private static Ordering orderingOf(String sort) {
    int comma = sort.indexOf(',');
    if (comma < 0 || sort.indexOf(',', comma + 1) >= 0) {
      throw refused("sort must be property,direction");
    }
    SortKey key =
        SortKey.parse(sort.substring(0, comma))
            .orElseThrow(() -> refused("sort must order by publicationDate or amount"));
    Sort.Order.Direction direction =
        directionOf(sort.substring(comma + 1))
            .orElseThrow(() -> refused("sort direction must be asc or desc"));
    return new Ordering(key, direction);
  }

  private static Optional<Sort.Order.Direction> directionOf(String published) {
    return switch (published) {
      case ASCENDING -> Optional.of(Sort.Order.Direction.ASC);
      case DESCENDING -> Optional.of(Sort.Order.Direction.DESC);
      default -> Optional.empty();
    };
  }

  /**
   * The contract's 1-based page as the store's 0-based one, carrying no ordering: the sort is the
   * use case's to build, and a pageable arriving with one would be a second source for a clause
   * that must have exactly one.
   *
   * <p>The {@code - 1} is the change of base and nothing else. It is written as a literal rather
   * than as {@link #MIN_PAGE}, which happens to be the same number for a different reason — the
   * smallest page a caller may ask for — so that raising the floor could never silently move the
   * origin with it.
   */
  private static Pageable pageableOf(int page, int size) {
    refusePagingOutsideItsRange(page, size);
    return Pageable.from(page - 1, size);
  }

  private static void refusePagingOutsideItsRange(int page, int size) {
    if (page < MIN_PAGE) {
      throw refused("page is 1-based, so it must be %d or greater".formatted(MIN_PAGE));
    }
    if (size < MIN_SIZE || size > MAX_SIZE) {
      throw refused("size must be between %d and %d".formatted(MIN_SIZE, MAX_SIZE));
    }
  }

  /**
   * A refusal that says which parameter it is about. Thrown as a problem rather than as a status,
   * because the framework's status handler drops the message: a caller would be told the request
   * was refused and left to work out which of five rules it broke, on an operation whose whole
   * design is that a wrong selection is never quietly answered.
   */
  private static ThrowableProblem refused(String why) {
    return Problem.builder()
        .withTitle("Bad Request")
        .withStatus(new HttpStatusType(HttpStatus.BAD_REQUEST))
        .withDetail(why)
        .build();
  }

  private record Ordering(SortKey key, Sort.Order.Direction direction) {
  }
}
