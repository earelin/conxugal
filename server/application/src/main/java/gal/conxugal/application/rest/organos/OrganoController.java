package gal.conxugal.application.rest.organos;

import gal.conxugal.application.rest.contratosmenores.ContratosMenoresSummaryResponse;
import gal.conxugal.domain.contrato.DescribeContratosMenoresSection;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.ViewOrgano;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.UUID;

/**
 * One Órgano and the contract families it holds visible data for — the single read an Órgano's
 * page is built from, where its name, its tabs and its opening section's year chooser all come
 * from one response rather than three round trips.
 *
 * <p><b>Composing the families is this controller's whole job, and it lives nowhere else.</b> Each
 * family's summary is its own feature's, asked for by its own port and serialised by its own
 * record; what this class adds is the envelope. Nothing generic stands in for <em>a family</em>:
 * there is one port today and a second family adds a second injection, which is cheaper than an
 * abstraction that would have to guess what they have in common before two of them exist.
 *
 * <p><b>Only an unknown id is refused.</b> {@link ViewOrgano} is what decides that, because
 * {@link DescribeContratosMenoresSection} answers an unknown Órgano exactly as it answers one
 * holding nothing and so cannot tell the two apart. An Órgano no contract of any family is visible
 * for is not an error: visibility is a property of the contracts, not of the reader, so it answers
 * with an empty families map — neither a 403 nor a 404, since nothing here makes an Órgano's
 * identity a secret.
 */
@Controller("/api/organo")
@Secured(SecurityRule.IS_AUTHENTICATED)
class OrganoController {

  private final ViewOrgano viewOrgano;
  private final DescribeContratosMenoresSection contratosMenoresSection;

  OrganoController(
      ViewOrgano viewOrgano, DescribeContratosMenoresSection contratosMenoresSection) {
    this.viewOrgano = viewOrgano;
    this.contratosMenoresSection = contratosMenoresSection;
  }

  @Get("/{id}")
  OrganoMemberResponse read(@PathVariable UUID id) {
    OrganoId organoId = new OrganoId(id);
    return OrganoMemberResponse.of(viewOrgano.view(organoId), familiesOf(organoId));
  }

  private FamiliesResponse familiesOf(OrganoId organoId) {
    return new FamiliesResponse(
        contratosMenoresSection.describe(organoId)
            .map(ContratosMenoresSummaryResponse::of)
            .orElse(null));
  }
}
