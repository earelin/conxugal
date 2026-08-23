package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.validation.Preconditions;
import java.util.List;
import java.util.Objects;

/**
 * One page of an Órgano's published licitacións, and the Órgano's whole published count.
 *
 * <p>{@code recordsTotal} is the source's own figure for everything that Órgano has ever
 * published, so it is carried on every answer including one holding no entries at all. It is what
 * makes a walk's completeness provable rather than guessed at, and it moves while a multi-hour
 * import runs — a test re-read on every response, not a constant read once.
 *
 * <p>The entries are in the order the source returned them, which is the order that was asked for.
 * Nothing re-sorts them.
 */
public record LicitacionListingPage(List<LicitacionListingEntry> entries, long recordsTotal) {

  public LicitacionListingPage {
    Objects.requireNonNull(entries, "entries must not be null");
    Preconditions.requireNotNegative(recordsTotal, "recordsTotal");
    entries = List.copyOf(entries);
  }
}
