package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.ListOrganos;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import java.util.List;

/**
 * The catalogue as an administrator sees it — the same rows, the same name order, and the import
 * mark the shared read withholds. It reads the same use case that read serves, so the two orders
 * cannot drift apart; only the serialisation differs.
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
