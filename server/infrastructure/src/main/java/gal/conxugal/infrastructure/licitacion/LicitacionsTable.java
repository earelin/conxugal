package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.licitacion.LicitacionListingEntry;
import gal.conxugal.domain.licitacion.LicitacionListingPage;
import gal.conxugal.domain.licitacion.LicitacionListingUnavailableException;
import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The source's licitacións table response: the rows of the requested page, and the Órgano's whole
 * published count. Turns itself into the page the port answers with, as {@link LicitacionsRow}
 * does for one row.
 *
 * <p>Both fields are boxed so that a response missing either is distinguishable from one reporting
 * nothing — the first is a body that is not the documented shape, the second an Órgano that has
 * published nothing.
 */
@Serdeable.Deserializable
record LicitacionsTable(@Nullable Long recordsTotal, @Nullable List<LicitacionsRow> data) {

  /**
   * This response as the port's page, with the rows in the order the source returned them.
   *
   * <p>{@code recordsTotal} is range-checked here rather than left to the page's own constructor:
   * that would refuse it as an {@link IllegalArgumentException}, which the port reserves for a
   * page we asked for wrongly. A count the source published cannot be our mistake.
   *
   * @throws LicitacionListingUnavailableException if the response is not the documented shape
   */
  LicitacionListingPage toListingPage() {
    if (recordsTotal == null || recordsTotal < 0 || data == null) {
      throw new LicitacionListingUnavailableException(
          "Source response is not the documented shape: recordsTotal is missing or negative, or "
              + "data is missing");
    }
    List<LicitacionListingEntry> entries = new ArrayList<>(data.size());
    for (LicitacionsRow row : data) {
      if (row == null) {
        throw new LicitacionListingUnavailableException(
            "Source response is not the documented shape: a row is missing");
      }
      entries.add(row.toListingEntry());
    }
    return new LicitacionListingPage(entries, recordsTotal);
  }
}
