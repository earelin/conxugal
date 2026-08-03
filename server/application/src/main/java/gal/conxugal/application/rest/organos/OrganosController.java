package gal.conxugal.application.rest.organos;

import gal.conxugal.domain.organo.ListOrganos;
import gal.conxugal.domain.organo.taxonomia.ListTermos;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

/**
 * The catalogue and its taxonomía, read by any authenticated caller — reading them is not an
 * administration capability, unlike every operation that changes them. Each read is one
 * table serialised row for row, in the name order the repository delivers: nothing here
 * nests, groups, partitions or re-sorts, and a caller joins the two lists on
 * {@code termoId} to build the tree.
 */
@Controller("/api/organos")
@Secured(SecurityRule.IS_AUTHENTICATED)
class OrganosController {

  private final ListOrganos listOrganos;
  private final ListTermos listTermos;

  OrganosController(ListOrganos listOrganos, ListTermos listTermos) {
    this.listOrganos = listOrganos;
    this.listTermos = listTermos;
  }

  @Get
  List<OrganoResponse> list() {
    return listOrganos.list()
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
