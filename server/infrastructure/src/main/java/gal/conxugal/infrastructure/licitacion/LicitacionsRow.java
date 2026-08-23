package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.licitacion.LicitacionListingEntry;
import gal.conxugal.domain.licitacion.LicitacionListingUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.money.Money;
import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.jspecify.annotations.Nullable;

/**
 * One row of the source's licitacións table, named and shaped as the source publishes it, and the
 * one place that shape is turned into the value the port answers with.
 *
 * <p>{@code importe} binds as a {@link BigDecimal} rather than a floating-point number: the scale
 * the source published is part of the amount, and a {@code double} would lose it before the value
 * ever reached a {@link Money}. It is the procedure's base budget, and the entry it becomes says
 * so — the awarded amounts are on the per-procedure record and appear nowhere here.
 *
 * <p>{@code id} binds as text although every identifier measured is a JSON number, because that is
 * what {@link PublicationId} is: how the source mints them is the source's business, and one that
 * started issuing alphanumeric identifiers should cost a parse here rather than fail every page as
 * an undecodable body. The binding coerces the number it does publish.
 *
 * <p>{@code draw} and {@code recordsFiltered} are not bound — nothing above the port reads either.
 */
@Serdeable.Deserializable
record LicitacionsRow(
    @Nullable String id,
    @Nullable String publicado,
    @Nullable String modificado,
    @Nullable String objeto,
    @Nullable BigDecimal importe,
    @Nullable Integer estado,
    @Nullable String estadoDesc) {

  /**
   * Strict, because the default resolver does not reject an impossible day of the month — it
   * clamps it, turning a published {@code 31-02} into the 28th and storing a date nobody
   * published. A date that cannot be read is meant to be absent, never invented.
   *
   * <p>The listing's form only. The per-procedure record publishes its dates with a time beside
   * them, which is a different pattern read somewhere else.
   */
  private static final DateTimeFormatter PUBLISHED_DATE =
      DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

  /**
   * This row as the port's value, and the only place the system narrows what the listing
   * published.
   *
   * <p>Every text value is stripped of the padding the source pads with, and one left empty once
   * stripped is carried as absent, so everything above the port sees one absent case rather than
   * an empty string alongside a null. Nothing else is touched — no case folding, no collapsing of
   * internal spacing, no bound on the object at any length.
   *
   * <p>Both dates are interpreted here, because they are the values the aggregate does not store
   * as text. Text that cannot be read as a date is carried as absent rather than failing the row:
   * the column is nullable at the source and a real procedure is not refused over a date nobody
   * can use.
   *
   * @throws LicitacionListingUnavailableException if the row carries no usable identifier or no
   *     state code — the two fields nothing downstream can do without, since a procedure is matched
   *     on the first and cannot be stored without the second. The identifier is judged here rather
   *     than left to {@link PublicationId}, which refuses a blank one as an
   *     {@link IllegalArgumentException} — the exception this port reserves for a page we asked
   *     for wrongly, which a value the source published can never be.
   */
  LicitacionListingEntry toListingEntry() {
    String publicationId = published(id);
    if (publicationId == null) {
      throw new LicitacionListingUnavailableException(
          "Source response is not the documented shape: a row carries no id");
    }
    if (estado == null) {
      throw new LicitacionListingUnavailableException(
          "Source response is not the documented shape: a row carries no estado");
    }
    return new LicitacionListingEntry(
        new PublicationId(publicationId),
        publishedDate(publicado),
        publishedDate(modificado),
        published(objeto),
        importe == null ? null : new Money(importe),
        estado,
        published(estadoDesc));
  }

  /** A value as the source published it, minus the padding it pads with. */
  private static @Nullable String published(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String stripped = Whitespace.strip(value);
    return stripped.isEmpty() ? null : stripped;
  }

  private static @Nullable LocalDate publishedDate(@Nullable String value) {
    String text = published(value);
    if (text == null) {
      return null;
    }
    try {
      return LocalDate.parse(text, PUBLISHED_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
