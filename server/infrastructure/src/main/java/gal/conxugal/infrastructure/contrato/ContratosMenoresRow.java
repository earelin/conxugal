package gal.conxugal.infrastructure.contrato;

import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import gal.conxugal.domain.money.Money;
import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.jspecify.annotations.Nullable;

/**
 * One row of the source's contratos menores table, named and shaped as the source publishes it,
 * and the one place that shape is turned into the value the port answers with.
 *
 * <p>{@code importe} binds as a {@link BigDecimal} rather than a floating-point number: the scale
 * the source published is part of the amount, and a {@code double} would lose it before the value
 * ever reached a {@link Money}.
 *
 * <p>{@code draw} and {@code recordsFiltered} are not bound — nothing above the port reads either.
 */
@Serdeable.Deserializable
record ContratosMenoresRow(
    @Nullable Long id,
    @Nullable String publicado,
    @Nullable String objeto,
    @Nullable BigDecimal importe,
    @Nullable String nif,
    @Nullable String adjudicatario,
    @Nullable String duracion) {

  /**
   * The source publishes short phrases in {@code duracion} ({@code "1 mes"}), so this is generous
   * and is not expected to fire. It exists so an unexpectedly long value loses its tail instead of
   * failing a batch and rejecting a real award, and it is applied here because no column bounds it.
   */
  static final int MAX_DURATION_LENGTH = 64;

  /**
   * Strict, because the default resolver does not reject an impossible day of the month — it
   * clamps it, turning a published {@code 31-02} into the 28th and storing a date nobody
   * published. A date that cannot be read is meant to be absent, never invented.
   */
  private static final DateTimeFormatter PUBLISHED_DATE =
      DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

  /**
   * This row as the port's value, and the only place the system narrows what the source published.
   *
   * <p>Every text value is stripped of the padding the source uses to fill its fixed-width fields,
   * and one left empty once stripped is carried as absent, so everything above the port sees one
   * absent case rather than an empty string alongside a null. The duration is capped. Nothing else
   * is touched — no case folding, no collapsing of internal spacing, no bound on the object at any
   * length.
   *
   * <p>The publication date is the one value interpreted here, because it is the one the aggregate
   * does not store as text. Text that cannot be read as a date is carried as absent rather than
   * failing the row: a real award is not refused over a date nobody can use.
   *
   * @throws ContratoMenorSourceUnavailableException if the row carries no identifier, which is the
   *     one field nothing downstream can do without
   */
  ContratoMenorSourceEntry toSourceEntry() {
    if (id == null) {
      throw new ContratoMenorSourceUnavailableException(
          "Source response is not the documented shape: a row carries no id");
    }
    return new ContratoMenorSourceEntry(
        id,
        publicationDate(publicado),
        published(objeto),
        importe == null ? null : new Money(importe),
        capped(published(duracion)),
        published(adjudicatario),
        published(nif));
  }

  /** A value as the source published it, minus the padding it pads with. */
  private static @Nullable String published(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String stripped = Whitespace.strip(value);
    return stripped.isEmpty() ? null : stripped;
  }

  /**
   * The cut backs off a character rather than land between a surrogate pair, which would leave a
   * lone surrogate that PostgreSQL refuses as an invalid byte sequence — the failed batch this cap
   * exists to avoid. What the cut leaves is stripped again for the same reason it was stripped
   * before: a cut landing on a space would otherwise put back trailing whitespace that nothing
   * published.
   */
  private static @Nullable String capped(@Nullable String duration) {
    if (duration == null || duration.length() <= MAX_DURATION_LENGTH) {
      return duration;
    }
    int end = MAX_DURATION_LENGTH;
    if (Character.isHighSurrogate(duration.charAt(end - 1))) {
      end--;
    }
    return published(duration.substring(0, end));
  }

  private static @Nullable LocalDate publicationDate(@Nullable String value) {
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
