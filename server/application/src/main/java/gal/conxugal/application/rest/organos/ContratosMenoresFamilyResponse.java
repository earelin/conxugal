package gal.conxugal.application.rest.organos;

import gal.conxugal.application.rest.contratosmenores.ContratosMenoresSummaryResponse;
import gal.conxugal.domain.contrato.ContratosMenoresSection;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An Órgano's contratos menores as the page framing them needs them: where to mount the section,
 * and what the section says about itself.
 *
 * <p><b>The route travels as data rather than as the key above it.</b> The key names the family
 * and stays stable; this names the path segment the family's section is mounted at. Keeping them
 * apart is what leaves the client with no table mapping one to the other — it reads the segment
 * out of the response and builds its links and its tab from that, so nothing it holds can disagree
 * with the response about where a family lives.
 *
 * <p><b>The summary is nested rather than spread beside the route</b>, because its shape is
 * FEAT-0011's and not this page's. Flattening the two would have this record restate fields it
 * does not own, and would put a field of the page's into a schema the family publishes. The page
 * reads the route and hands the summary on untouched.
 */
@Serdeable
public record ContratosMenoresFamilyResponse(
    String route, ContratosMenoresSummaryResponse summary) {

  /** Matches the client's child route, {@code /organo/{id}/contratos-menores}. */
  private static final String ROUTE = "contratos-menores";

  static ContratosMenoresFamilyResponse of(ContratosMenoresSection section) {
    return new ContratosMenoresFamilyResponse(
        ROUTE, ContratosMenoresSummaryResponse.of(section));
  }
}
