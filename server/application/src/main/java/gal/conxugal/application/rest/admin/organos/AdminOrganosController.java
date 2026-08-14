package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.ListOrganos;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import java.util.List;

/**
 * The catalogue as an administrator sees it — <strong>every</strong> Órgano, with the import mark
 * and import state the shared read withholds. This is the read that carries the whole catalogue:
 * {@code GET /api/organos} is narrowed to the Órganos holding a visible contract, and an
 * administrator filing the ones that hold none needs to see them. Both orders come from the same
 * repository method, so neither can drift from the other's collation.
 */
@Controller("/api/admin/organos")
@Secured("ADMIN")
class AdminOrganosController {

  private final ListOrganos listOrganos;

  AdminOrganosController(ListOrganos listOrganos) {
    this.listOrganos = listOrganos;
  }

  @Get
  List<AdminOrganoResponse> list() {
    return listOrganos.list()
        .stream()
        .map(AdminOrganoResponse::of)
        .toList();
  }
}
