package gal.conxugal.application.rest.contratosmenores;

import static gal.conxugal.application.rest.paging.PagingParameters.pageableOf;
import static gal.conxugal.application.rest.request.Refusals.refuseUnknownParameters;
import static gal.conxugal.application.rest.request.Refusals.refused;

import gal.conxugal.application.rest.paging.PagedResponse;
import gal.conxugal.application.rest.paging.SortParameter;
import gal.conxugal.domain.contrato.ListContratosMenores;
import gal.conxugal.domain.contrato.SortKey;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import gal.conxugal.domain.contrato.YearSelection;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.Set;
import java.util.UUID;

/**
 * One Órgano's contratos menores, a year at a time. Open to any authenticated caller — the read is
 * granted to {@code USER} and {@code ADMIN} alike, it modifies nothing, and requiring a session at
 * all is the mitigation the whole surface rests on.
 *
 * <p><b>The driving side is where the two paging vocabularies meet, and this is its one operation
 * so far.</b> Inbound, the contract's parameters become a year, a sort key, a direction and a
 * 0-based unsorted pageable; outbound, the store's page becomes the 1-based envelope. Nothing
 * above this layer sees a pageable and nothing below it sees the envelope, so an off-by-one has
 * exactly one place to be. What is <em>this</em> operation's is parsed here — its year, and which
 * properties its {@code sort} may name; how the three parameters three specifications share are
 * spelled belongs to {@link gal.conxugal.application.rest.paging.PagingParameters} and
 * {@link gal.conxugal.application.rest.paging.SortParameter}, beside the
 * {@link gal.conxugal.application.rest.paging.PagedResponse} they answer with, so the second list
 * cannot page differently from the first.
 *
 * <p><b>Nothing binds a pageable from the request, and that is a security invariant rather than a
 * preference.</b> The framework's binder accepts any property name and silently degrades an
 * unrecognised direction to ascending; the browse statement is native SQL and appends a property
 * name to its {@code ORDER BY} verbatim and unescaped. So the ordering is carried by two enums the
 * whole way down, and <b>the property is matched against this operation's own closed set here,
 * before the domain call</b> — a shared type validates the parameter's shape, never its
 * vocabulary, because only the operation knows which names it has. The mechanism by which a
 * caller's text could reach a query is absent rather than guarded, and the shared pageable is
 * built with no ordering at all.
 *
 * <p><b>Every refusal is a refusal.</b> A missing or malformed year, a {@code sort} naming a
 * property this read does not offer, a parameter it does not have: each is a {@code 400} raised
 * here, and the shape of {@code sort}, {@code page} and {@code size} is refused on the same terms
 * where each is read. None is corrected to something answerable. An answer that quietly differed
 * from the request that produced it would be worse than an error, because nothing on the wire
 * would say so — the ordering is not echoed back, the URL being what carries it.
 *
 * <p>A page beyond the last is <b>not</b> among them: it is a legitimate question about a real
 * selection, and it is answered with an empty page carrying that selection's true totals, which is
 * what lets a client clamp rather than guess.
 */
@Controller("/api/organo")
@Secured(SecurityRule.IS_AUTHENTICATED)
class ContratosMenoresController {

  private static final String DEFAULT_SORT = "publicationDate,desc";
  private static final Set<String> ACCEPTED_PARAMETERS = Set.of("year", "sort", "page", "size");

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
    SortParameter ordering = SortParameter.of(sort);
    Pageable pageable = pageableOf(page, size);

    Page<VisibleContratoMenor> answered =
        listContratosMenores.list(
            new OrganoId(id), selection, keyOf(ordering.property()), ordering.direction(),
            pageable);

    return PagedResponse.of(answered, row -> ContratoMenorResponse.of(row, publication));
  }

  private static YearSelection yearOf(@Nullable String year) {
    return YearSelection.parse(year)
        .orElseThrow(
            () -> refused(
                "year is required and must be a four-digit year: there is no all-years list"));
  }

  /**
   * The sort key this operation offers, or a refusal — never a fallback to the default. The
   * property is parsed by the domain's own {@code parse}, so the set the endpoint accepts is the
   * set the domain has rather than a second list kept in step with it by hand — and it is that
   * matching, not the parameter's spelling, that keeps a caller's text out of an
   * {@code ORDER BY} a native statement appends verbatim.
   */
  private static SortKey keyOf(String property) {
    return SortKey.parse(property)
        .orElseThrow(() -> refused("sort must order by publicationDate or amount"));
  }
}
