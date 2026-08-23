package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.licitacion.LicitacionListingOrder;
import gal.conxugal.domain.licitacion.LicitacionListingPage;
import gal.conxugal.domain.licitacion.LicitacionListingSource;
import gal.conxugal.domain.licitacion.LicitacionListingUnavailableException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.util.Objects;

/**
 * {@link LicitacionListingSource} adapter for contratosdegalicia.gal, against the API measured in
 * the feature's source contract. The {@code organismo} path segment is the {@code sourceKey} the
 * catalogue already stores, so nothing here maps one identifier to another.
 *
 * <p>This class owns the page it is willing to ask for and the exchange that fetches it. What an
 * answer means is the response's own: {@link LicitacionsTable} turns itself into the page and
 * {@link LicitacionsRow} into one entry, which is where the values the source publishes are
 * narrowed to the ones the port answers with.
 */
@Singleton
public class ContratosDeGaliciaLicitacionListingSourceAdapter implements LicitacionListingSource {

  /** The largest page the source answers: beyond it, it replies {@code 500}. */
  static final int MAX_PAGE_SIZE = 100;

  private static final int DRAW = 1;

  private final LicitacionsClient licitacionsClient;

  public ContratosDeGaliciaLicitacionListingSourceAdapter(LicitacionsClient licitacionsClient) {
    this.licitacionsClient = licitacionsClient;
  }

  @Override
  public LicitacionListingPage fetchPage(
      String sourceKey, LicitacionListingOrder order, int offset, int pageSize) {
    requirePageWithinSourceLimits(sourceKey, order, offset, pageSize);

    return fetchTable(sourceKey, order, offset, pageSize).toListingPage();
  }

  /**
   * The source's page limit, honoured by construction rather than discovered. Asking beyond it
   * answers a bare {@code 500} with no machine-readable body, indistinguishable from a server
   * fault — so a bug of ours would otherwise be counted against the source's health. Refused, not
   * clamped: a page silently narrowed is a walk that reads a different history than it asked for.
   */
  private static void requirePageWithinSourceLimits(
      String sourceKey, LicitacionListingOrder order, int offset, int pageSize) {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    Objects.requireNonNull(order, "order must not be null");
    if (sourceKey.isBlank()) {
      throw new IllegalArgumentException("sourceKey must not be blank");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("offset must not be negative, was %d".formatted(offset));
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "pageSize must be between 1 and %d, was %d".formatted(MAX_PAGE_SIZE, pageSize));
    }
  }

  private LicitacionsTable fetchTable(
      String sourceKey, LicitacionListingOrder order, int offset, int pageSize) {
    TableOrder tableOrder = tableOrder(order);
    try {
      return readableTable(
          licitacionsClient.table(
              sourceKey, offset, pageSize, DRAW, tableOrder.column(), tableOrder.direction()));
    } catch (HttpClientResponseException e) {
      throw new LicitacionListingUnavailableException(
          "Source responded with status %s".formatted(e.getStatus()), e);
    } catch (HttpClientException e) {
      throw new LicitacionListingUnavailableException(
          "Source is unreachable: %s".formatted(e.getMessage()), e);
    }
  }

  /**
   * The answer, once it is established that there is one. Everything a failed exchange throws is
   * the exchange's own to report; what reaches here is a response the client accepted, which may
   * still be unusable.
   */
  private static LicitacionsTable readableTable(HttpResponse<LicitacionsTable> response) {
    // The one error status that arrives as a response rather than an exception: a declarative
    // client reads 404 as an absent value, so judging it is the adapter's to do.
    if (response.code() >= HttpStatus.BAD_REQUEST.getCode()) {
      throw new LicitacionListingUnavailableException(
          "Source responded with status %s".formatted(response.getStatus()));
    }
    LicitacionsTable table = response.body();
    if (table == null) {
      // Both the empty body and the undecodable one arrive here, and nothing distinguishes them
      // by this point, so the message says so rather than send a reader looking for one of them.
      throw new LicitacionListingUnavailableException(
          "Source response carried no readable body: it was empty or could not be decoded");
    }
    return table;
  }

  /**
   * How one order is spelled on the wire: the column's index in the payload the client always
   * sends, and the direction beside it. The two travel together so that a second order cannot
   * acquire the column of one and the direction of another — a pair that would compile, request a
   * real page, and return the wrong one.
   */
  private record TableOrder(int column, String direction) {}

  private static TableOrder tableOrder(LicitacionListingOrder order) {
    return switch (order) {
      case ID_ASCENDING -> new TableOrder(LicitacionsClient.ID_COLUMN, LicitacionsClient.ASCENDING);
    };
  }
}
