package gal.conxugal.application.rest.contratosmenores;

import static gal.conxugal.application.rest.request.Refusals.refuseUnknownParameters;
import static gal.conxugal.application.rest.request.Refusals.refused;

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
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
  private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d{1,10}");
  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_SIZE = 50;
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
      @QueryValue @Nullable String page,
      @QueryValue @Nullable String size) {
    refuseUnknownParameters(request, ACCEPTED_PARAMETERS);
    YearSelection selection = yearOf(year);
    Ordering ordering = orderingOf(sort);
    Pageable pageable =
        pageableOf(
            wholeNumberOf("page", page, DEFAULT_PAGE), wholeNumberOf("size", size, DEFAULT_SIZE));

    Page<VisibleContratoMenor> answered =
        listContratosMenores.list(
            new OrganoId(id), selection, ordering.key(), ordering.direction(), pageable);

    return PagedResponse.of(answered, row -> ContratoMenorResponse.of(row, publication));
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
   * A paging parameter as the number it claims to be, its default when it was not sent at all, or
   * a refusal.
   *
   * <p><b>Both arrive as text on purpose</b>, and the framework's own conversion is what that
   * avoids. Bound as an {@code int} with a {@code defaultValue}, a value the converter cannot read
   * is not refused — the binder falls back to the default — so {@code ?size=} was answered with
   * fifty rows and a body saying {@code "size": 50}, which is the wrong answer to a question
   * nobody asked. Absent and empty are different requests and only the first has a default.
   *
   * <p>The pattern does the refusing rather than {@link Integer#parseInt}, for the reason a year
   * is parsed the same way: {@code parseInt} accepts a leading {@code +} and digits from every
   * script Unicode defines, none of which the contract offers. Ten digits is the widest a
   * {@code format: int32} parameter can be, and anything beyond what an {@code int} holds is
   * refused rather than wrapped.
   */
  private static int wholeNumberOf(String name, @Nullable String published, int whenAbsent) {
    if (published == null) {
      return whenAbsent;
    }
    if (WHOLE_NUMBER.matcher(published).matches()) {
      long value = Long.parseLong(published);
      if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
        return (int) value;
      }
    }
    throw refused("%s must be a whole number".formatted(name));
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

  private record Ordering(SortKey key, Sort.Order.Direction direction) {
  }
}
