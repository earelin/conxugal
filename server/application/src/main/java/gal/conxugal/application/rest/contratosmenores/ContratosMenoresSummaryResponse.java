package gal.conxugal.application.rest.contratosmenores;

import gal.conxugal.domain.contrato.ContratosMenoresSection;
import gal.conxugal.domain.contrato.YearSelection;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * What an Órgano's contratos menores section says about itself, as the Órgano member read carries
 * it: the years it offers, and the two statements a reader is owed about the data behind them.
 *
 * <p><b>Its presence is what says the section exists.</b> There is no <em>has contracts</em> flag
 * on the wire because there is none in the domain: an Órgano with no visible contrato menor is
 * described by nothing at all, so no entry appears in the families map and no tab is drawn. A
 * boolean beside the years could disagree with them; an absent entry cannot.
 *
 * <p><b>Two booleans, never one status.</b> They are orthogonal — an Órgano unmarked halfway
 * through its initial import is partial <em>and</em> no longer being refreshed — and a single enum
 * would have to lie about one of them in exactly that state.
 *
 * <p>The years travel as plain integers, newest first, in the order the section answered them:
 * that is the order the chooser shows and the first of them is the year the section opens on.
 *
 * <p>It lives with the family rather than with the page that publishes it, so the feature owning
 * contratos menores owns how one is serialised and the page composing the families map knows
 * nothing of how a year facet becomes JSON.
 */
@Serdeable
public record ContratosMenoresSummaryResponse(
    List<Integer> years, boolean partial, boolean updating) {

  public ContratosMenoresSummaryResponse {
    years = List.copyOf(years);
  }

  public static ContratosMenoresSummaryResponse of(ContratosMenoresSection section) {
    return new ContratosMenoresSummaryResponse(
        section.years().stream().map(YearSelection::year).toList(),
        section.partial(),
        section.updating());
  }
}
