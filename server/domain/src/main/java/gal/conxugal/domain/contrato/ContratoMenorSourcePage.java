package gal.conxugal.domain.contrato;

import gal.conxugal.commons.validation.Preconditions;
import java.util.List;
import java.util.Objects;

/**
 * One page of an Órgano's published contratos menores, and the Órgano's whole published count.
 *
 * <p>{@code recordsTotal} is the source's own figure for everything that Órgano has ever
 * published, independent of the window this page came from, so it is carried on every answer
 * including one whose window matched nothing. It is what makes an import's completeness provable
 * rather than guessed at, and it moves while a long import runs — a test, not a constant.
 */
public record ContratoMenorSourcePage(List<ContratoMenorSourceEntry> entries, long recordsTotal) {

  public ContratoMenorSourcePage {
    Objects.requireNonNull(entries, "entries must not be null");
    Preconditions.requireNotNegative(recordsTotal, "recordsTotal");
    entries = List.copyOf(entries);
  }
}
