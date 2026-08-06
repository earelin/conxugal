package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.contrato.ContratosMenoresImportStatus;
import gal.conxugal.domain.contrato.ListContratosMenoresImportState;
import gal.conxugal.domain.organo.ListOrganos;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import java.util.List;
import java.util.Map;

/**
 * The catalogue as an administrator sees it — the same rows, the same name order, and the import
 * mark and import state the shared read withholds. It reads the same use case that read serves, so
 * the two orders cannot drift apart; only the serialisation differs.
 *
 * <p>The states are read once for the whole catalogue rather than per Órgano, and an Órgano with
 * no state row is never started.
 */
@Controller("/api/admin/organos")
@Secured("ADMIN")
class AdminOrganosController {

  private final ListOrganos listOrganos;
  private final ListContratosMenoresImportState listImportState;

  AdminOrganosController(
      ListOrganos listOrganos, ListContratosMenoresImportState listImportState) {
    this.listOrganos = listOrganos;
    this.listImportState = listImportState;
  }

  @Get
  List<AdminOrganoResponse> list() {
    Map<OrganoId, ContratosMenoresImportStatus> importStates = listImportState.byOrgano();
    return listOrganos.list()
        .stream()
        .map(organo ->
            AdminOrganoResponse.of(
                organo, ListContratosMenoresImportState.statusOf(importStates, organo.id())))
        .toList();
  }
}
