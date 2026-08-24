package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One row of the record's resolution table, as the source published it and before it becomes an
 * {@link Award}. The award point it belongs to is named by {@code loteKey} — a lote where the
 * procedure has them, and {@code null} meaning <em>the procedure as a whole</em>, which is what
 * the table's {@code _} cell says and what 85 of 100 measured procedures publish.
 *
 * <p><strong>The awarded amount is this table's {@code Importe} and nothing else.</strong> The
 * listing's {@code importe} is the base budget — on 822054 it is {@code 3378552.09} against two
 * awards of {@code 3052743.72} and {@code 206996.66} — so a parse that let one stand in for the
 * other would fill every total with budgets, silently and plausibly. The figure is held as
 * published; the resolution table states no VAT basis on any of 119 measured rows, so none is
 * carried.
 *
 * <p><strong>{@code bidderCount} is the table's {@code Part.} column</strong>, and it is read here
 * rather than where the bidder list is parsed because this is the task that reads this table and
 * re-reading it for one column would duplicate the whole parse. It sits beside the key it belongs
 * to, so a cross-check against the bidder rows for that lote joins on {@link LoteKey}'s reduction
 * rather than on the raw cell — which matters: raw, that join agrees on 63 of 158 award rows and
 * normalised on all 158, and a mismatch is what sends a procedure to the outstanding ledger.
 *
 * <p><strong>The date is the row's {@code Data difusión}, and is named for that.</strong> It is
 * what {@link Award}'s resolution date is built from, and the two are not the same fact: the date
 * the resolution was <em>taken</em> is published only inside a tooltip on the resolution
 * document's link ({@code Documento da resolución con data do 10-07-2024}), as prose rather than
 * as a cell, and it is not read. On every captured record the two agree to the day, so nothing
 * measured distinguishes them — which is exactly why this is named for the column it comes from
 * rather than for the column it feeds. A procedure resolved on one day and diffused on another
 * would otherwise be stored as resolved on the later one, with the name asserting something the
 * source never said.
 *
 * <p>Every value is nullable and null means the source published nothing there, or published
 * something that could not be interpreted — neither is a reason to refuse the row. The execution
 * period is prose the source writes ({@code 12 meses}, {@code 2 meses 7 días}) rather than a
 * duration it publishes, so it is held as text.
 *
 * @param loteKey the award point this row names, reduced by {@link LoteKey}; null is the
 *     procedure as a whole
 * @param resolution the published {@code Resolución} cell — the source's own word for what was
 *     decided, such as <em>Adxudicado</em>
 * @param diffusionDate the row's {@code Data difusión}, which is what {@link Award}'s resolution
 *     date is built from
 * @param amount the awarded {@code Importe}
 * @param executionPeriod {@code Prazo de execución}, as published
 * @param awardeeName the published {@code Adxudicatario} — a name only, since this table carries
 *     no fiscal identifier on any measured row
 * @param bidderCount the {@code Part.} count for this award point
 */
public record PublishedAward(
    @Nullable String loteKey,
    @Nullable String resolution,
    @Nullable LocalDate diffusionDate,
    @Nullable Money amount,
    @Nullable String executionPeriod,
    @Nullable String awardeeName,
    @Nullable Integer bidderCount) {

  /**
   * The lote cell is reduced here rather than trusted from the caller, on
   * {@link gal.conxugal.domain.operador.FiscalIdentifier}'s reasoning: a rule that holds only
   * when its caller has already reduced is one that silently mismatches the day a caller has not.
   * {@link LoteKey#normalise} is idempotent, so a key that arrives reduced stays as it is.
   */
  public PublishedAward {
    loteKey = LoteKey.normalise(loteKey).orElse(null);
    resolution = PublishedText.orNullWhenBlank(resolution);
    executionPeriod = PublishedText.orNullWhenBlank(executionPeriod);
    awardeeName = PublishedText.orNullWhenBlank(awardeeName);
  }
}
