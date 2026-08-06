package gal.conxugal.domain.contrato;

import gal.conxugal.domain.money.Money;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One contrato menor as the source published it, before it becomes a {@link ContratoMenor}.
 * {@code sourceId} is the source's own identifier, which is what matches this contract across
 * imports.
 *
 * <p>Only the identifier is required. Every other value is nullable, and null means the source
 * published nothing there — including a publication date whose text could not be interpreted,
 * which is not a reason to refuse the row.
 *
 * <p>The awardee's name and fiscal identifier are carried here although {@link ContratoMenor} does
 * not store them: they live on the operador the contract is awarded to, and the derivation that
 * builds it has only this row to read them from.
 *
 * <p>Values arrive as the adapter normalised them — stripped of the padding the source uses to
 * fill its fixed-width fields, with the duration already capped. Nothing above this record trims,
 * folds or truncates anything further.
 */
public record ContratoMenorSourceEntry(
    long sourceId,
    @Nullable LocalDate publicationDate,
    @Nullable String obxecto,
    @Nullable Money amount,
    @Nullable String duration,
    @Nullable String awardeeName,
    @Nullable String awardeeFiscalId) {}
