package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.Embeddable;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Which contract a published name was taken from: its publication date and its source
 * identifier. A {@code null date} is a contract whose publication date could not be interpreted.
 *
 * <p>Both values are carried, never the date alone: the name an operador is displayed under is
 * chosen by publication date and settled on the higher source identifier when two contracts
 * share one, so a name holding only a date could not be ordered against a name sharing it, and
 * two names seen only on undated contracts could not be ordered at all. The principal name and
 * the retained ones carry this same pair so one comparison orders both.
 *
 * <p>That comparison lives outside this type, with the other rules over published values.
 */
@Embeddable
public record NomeRank(@Nullable LocalDate date, long sourceId) {
}
