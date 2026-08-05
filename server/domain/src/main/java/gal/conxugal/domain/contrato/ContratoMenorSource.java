package gal.conxugal.domain.contrato;

import java.time.LocalDate;

/**
 * Port for retrieving an Órgano's published contratos menores. Implemented by an adapter in the
 * {@code infrastructure} module.
 *
 * <p>One slice per call, because that is the only shape the source offers: an Órgano, a date
 * window and a page within it. The walk belongs to the use case — this port neither iterates nor
 * remembers where it got to.
 *
 * <p>Answers the page's rows, or throws {@link ContratoMenorSourceUnavailableException} — never an
 * empty success standing in for a failure. A window that genuinely matched nothing is an ordinary
 * answer, and it still carries {@code recordsTotal}.
 */
public interface ContratoMenorSource {

  /**
   * One page of the contratos menores an Órgano published within a window.
   *
   * @param sourceKey the Órgano's key as the catalogue stores it
   * @param from the first day of the window, inclusive
   * @param to the last day of the window, inclusive
   * @param offset how many rows of the window to skip, zero-based
   * @param pageSize how many rows to ask for
   * @throws ContratoMenorSourceUnavailableException if the source is unreachable or its response
   *     cannot be read
   * @throws IllegalArgumentException if the slice asks for more than the source allows
   */
  ContratoMenorSourcePage fetchPage(
      String sourceKey, LocalDate from, LocalDate to, int offset, int pageSize);
}
