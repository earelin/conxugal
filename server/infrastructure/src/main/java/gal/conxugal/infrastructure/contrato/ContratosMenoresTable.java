package gal.conxugal.infrastructure.contrato;

import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The source's contratos menores table response: the rows of the requested page, and the Órgano's
 * whole published count. Turns itself into the page the port answers with, as {@link
 * ContratosMenoresRow} does for one row.
 *
 * <p>Both fields are boxed so that a response missing either is distinguishable from one reporting
 * nothing — the first is a body that is not the documented shape, the second an ordinary empty
 * page.
 */
@Serdeable.Deserializable
record ContratosMenoresTable(
    @Nullable Long recordsTotal, @Nullable List<ContratosMenoresRow> data) {

  /**
   * This response as the port's page.
   *
   * <p>{@code recordsTotal} is range-checked here rather than left to the page's own constructor:
   * that would refuse it as an {@link IllegalArgumentException}, which the port reserves for a
   * slice we asked for wrongly. A count the source published cannot be our mistake.
   *
   * @throws ContratoMenorSourceUnavailableException if the response is not the documented shape
   */
  ContratoMenorSourcePage toSourcePage() {
    if (recordsTotal == null || recordsTotal < 0 || data == null) {
      throw new ContratoMenorSourceUnavailableException(
          "Source response is not the documented shape: recordsTotal is missing or negative, or "
              + "data is missing");
    }
    List<ContratoMenorSourceEntry> entries = new ArrayList<>(data.size());
    for (ContratosMenoresRow row : data) {
      if (row == null) {
        throw new ContratoMenorSourceUnavailableException(
            "Source response is not the documented shape: a row is missing");
      }
      entries.add(row.toSourceEntry());
    }
    return new ContratoMenorSourcePage(entries, recordsTotal);
  }
}
