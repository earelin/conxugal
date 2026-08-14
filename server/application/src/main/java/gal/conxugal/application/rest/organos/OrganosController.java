package gal.conxugal.application.rest.organos;

import gal.conxugal.domain.organo.ListVisibleOrganos;
import gal.conxugal.domain.organo.taxonomia.ListTermos;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

/**
 * The visible set and the taxonomía it is filed into, read by any authenticated caller. The
 * catalogue read here is narrowed to the Órganos holding at least one visible contract, of any
 * family; the whole catalogue is an administration read, served by
 * {@code GET /api/admin/organos}.
 *
 * <p>The narrowing is <strong>this path's, not the caller's</strong>: an {@code ADMIN} reading it
 * gets the same set a {@code USER} gets and reaches the whole catalogue by calling the
 * {@code ADMIN}-gated read instead. A role check here would give one path two meanings.
 *
 * <p>Each read is a flat list in the name order the repository delivers: nothing here nests,
 * groups, partitions or re-sorts, and a caller joins the two lists on {@code termoId} to build the
 * tree. The taxonomía is served whole — a term is not hidden because the narrowing left it empty,
 * which is a decision for whatever draws the tree.
 */
@Controller("/api/organos")
@Secured(SecurityRule.IS_AUTHENTICATED)
class OrganosController {

  private final ListVisibleOrganos listVisibleOrganos;
  private final ListTermos listTermos;

  OrganosController(ListVisibleOrganos listVisibleOrganos, ListTermos listTermos) {
    this.listVisibleOrganos = listVisibleOrganos;
    this.listTermos = listTermos;
  }

  @Get
  List<OrganoResponse> list() {
    return listVisibleOrganos.list()
        .stream()
        .map(OrganoResponse::of)
        .toList();
  }

  @Get("/taxonomia")
  List<TermoResponse> taxonomia() {
    return listTermos.list()
        .stream()
        .map(TermoResponse::of)
        .toList();
  }
}
