package gal.conxugal.domain.contrato;

import java.util.List;
import java.util.Objects;

/**
 * What an Órgano's contratos menores section says about itself, answered before any contract is
 * fetched: the years it offers, and the two statements a reader is owed about the data behind
 * them.
 *
 * <p><b>There is no <em>has contracts</em> flag, and the absence is the design.</b> A section
 * exists exactly when there is a year to open it on, so its presence is carried by whether one of
 * these was produced at all rather than by a field inside it that could disagree with the years
 * beside it. That is also why the constructor refuses an empty list: <em>once the section is
 * present it is never empty</em> is a property of this type, not a rule its one producer has to
 * remember — the chooser offers only years that have contracts, so no choice a reader can make
 * produces an empty list.
 *
 * <p><b>Two booleans, never one status.</b> They are orthogonal, and an Órgano unmarked halfway
 * through its initial import is the case that proves it: what is shown is partial <em>and</em> it
 * is no longer being refreshed, both at once. A single enum would have to lie about one of them in
 * exactly that state.
 *
 * <p>{@code updating} is named for what a reader needs to know — <em>this data is still being
 * refreshed</em> — rather than for the administrator's mark it happens to be derived from today.
 * That the two coincide is an implementation fact and not something this type promises.
 */
public record ContratosMenoresSection(
    List<YearSelection> years, boolean partial, boolean updating) {

  public ContratosMenoresSection {
    Objects.requireNonNull(years, "years must not be null");
    if (years.isEmpty()) {
      throw new IllegalArgumentException(
          "a section offers at least one year: an Órgano with none has no section at all");
    }
    years = List.copyOf(years);
  }
}
