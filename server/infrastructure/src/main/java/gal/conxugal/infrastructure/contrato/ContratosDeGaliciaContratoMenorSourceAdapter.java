package gal.conxugal.infrastructure.contrato;

import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link ContratoMenorSource} adapter for contratosdegalicia.gal, against the API measured in the
 * feature's source contract. The {@code organismo} path segment is the {@code sourceKey} the
 * catalogue already stores, so nothing here maps one identifier to another.
 *
 * <p>This class owns the request and the judgement of whether an answer is usable at all; what one
 * published row means is {@link ContratosMenoresRow}'s, which is where the values the source
 * publishes are narrowed to the ones the port answers with.
 */
@Singleton
public class ContratosDeGaliciaContratoMenorSourceAdapter implements ContratoMenorSource {

  /** The widest window the source answers: beyond it, it replies {@code 500}. */
  static final int MAX_WINDOW_MONTHS = 3;

  /** The largest page the source answers: beyond it, it replies {@code 500}. */
  static final int MAX_PAGE_SIZE = 100;

  private static final int DRAW = 1;

  private final ContratosMenoresClient contratosMenoresClient;

  public ContratosDeGaliciaContratoMenorSourceAdapter(
      ContratosMenoresClient contratosMenoresClient) {
    this.contratosMenoresClient = contratosMenoresClient;
  }

  @Override
  public ContratoMenorSourcePage fetchPage(
      String sourceKey, LocalDate from, LocalDate to, int offset, int pageSize) {
    requireSliceWithinSourceLimits(sourceKey, from, to, offset, pageSize);

    ContratosMenoresTable table = fetchTable(sourceKey, from, to, offset, pageSize);
    Long recordsTotal = table.recordsTotal();
    List<ContratosMenoresRow> rows = table.data();
    if (recordsTotal == null || rows == null) {
      throw new ContratoMenorSourceUnavailableException(
          "Source response is not the documented shape: recordsTotal or data is missing");
    }

    List<ContratoMenorSourceEntry> entries = new ArrayList<>(rows.size());
    for (ContratosMenoresRow row : rows) {
      if (row == null) {
        throw new ContratoMenorSourceUnavailableException(
            "Source response is not the documented shape: a row is missing");
      }
      entries.add(row.toSourceEntry());
    }
    return new ContratoMenorSourcePage(entries, recordsTotal);
  }

  /**
   * The source's window and page limits, honoured by construction rather than discovered. Asking
   * beyond either answers a bare {@code 500} with no machine-readable body, indistinguishable from
   * a server fault — so a bug of ours would otherwise be counted against the source's health.
   */
  private static void requireSliceWithinSourceLimits(
      String sourceKey, LocalDate from, LocalDate to, int offset, int pageSize) {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    if (sourceKey.isBlank()) {
      throw new IllegalArgumentException("sourceKey must not be blank");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException(
          "window must not end before it starts, was %s to %s".formatted(from, to));
    }
    if (to.isAfter(from.plusMonths(MAX_WINDOW_MONTHS))) {
      throw new IllegalArgumentException(
          "window must not exceed %d months, was %s to %s"
              .formatted(MAX_WINDOW_MONTHS, from, to));
    }
    if (offset < 0) {
      throw new IllegalArgumentException("offset must not be negative, was %d".formatted(offset));
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "pageSize must be between 1 and %d, was %d".formatted(MAX_PAGE_SIZE, pageSize));
    }
  }

  private ContratosMenoresTable fetchTable(
      String sourceKey, LocalDate from, LocalDate to, int offset, int pageSize) {
    try {
      HttpResponse<ContratosMenoresTable> response =
          contratosMenoresClient.table(
              sourceKey,
              from.format(DateTimeFormatter.ISO_LOCAL_DATE),
              to.format(DateTimeFormatter.ISO_LOCAL_DATE),
              offset,
              pageSize,
              DRAW);
      // The one error status that arrives as a response rather than an exception: a declarative
      // client reads 404 as an absent value, so judging it is the adapter's to do.
      if (response.code() >= HttpStatus.BAD_REQUEST.getCode()) {
        throw new ContratoMenorSourceUnavailableException(
            "Source responded with status %s".formatted(response.getStatus()));
      }
      ContratosMenoresTable table = response.body();
      if (table == null) {
        throw new ContratoMenorSourceUnavailableException("Source returned an empty response body");
      }
      return table;
    } catch (HttpClientResponseException e) {
      throw new ContratoMenorSourceUnavailableException(
          "Source responded with status %s".formatted(e.getStatus()), e);
    } catch (HttpClientException e) {
      throw new ContratoMenorSourceUnavailableException(
          "Source is unreachable: %s".formatted(e.getMessage()), e);
    }
  }
}
