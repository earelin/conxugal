package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.OperadorId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the source published in the resolution table for one award point — a lote where the
 * procedure has them, the procedure itself where it does not. {@code id} is a system-assigned
 * identity, {@code null} only until the database assigns it.
 *
 * <p><strong>The awarded amount is the resolution table's {@code Importe} and nothing
 * else.</strong> The listing's {@code importe} is the base budget, which for 822054 is 3 378 552,09
 * against two awards of 3 052 743,72 and 206 996,66 — so a model that let one stand in for the
 * other would fill every cross-family total with budgets, silently and plausibly. The figure is
 * held as published; whether it includes VAT the source does not state, and nothing here asserts
 * it.
 *
 * <p><strong>An award that names nobody is a supported outcome, not a failure.</strong> Over 284
 * measured award rows the resolution table carried no fiscal identifier at all, so the operador is
 * reached through the formalisation, the bidder list or a catalogue match on the published name —
 * and 36% of rows have only the name. Where no route reaches one, the operador reference is null,
 * {@link #awardeeResolutionPath()} is {@link AwardeeResolutionPath#UNRESOLVED}, and the licitación
 * is stored and stays visible showing an award naming nobody.
 *
 * <p><strong>The resolution path is a field rather than a derivation</strong>, because a derived
 * link has to stay distinguishable from one the source published and reversible without guessing
 * which it was. It is never null: {@code UNRESOLVED} is the value for an award nothing resolved,
 * so the absence of a link is stated rather than inferred from a null.
 *
 * <p><strong>The two are a biconditional, and it is enforced here.</strong> An award names an
 * operador exactly when a route reached one — so neither a null operador beside
 * {@code PUBLISHED_BY_FORMALISATION} nor an operador beside {@code UNRESOLVED} can be built. The
 * case worth checking before asserting it is a published identifier that resolves to nobody the
 * catalogue holds, and there is no such case: SPEC-0006 R3's resolution catalogues an identifier
 * nothing named before rather than failing to find it, so every route that answers at all answers
 * with an operador. The one route that can decline — a catalogue match on the name — declines by
 * reaching no operador and leaving the path {@code UNRESOLVED}, which is the same statement from
 * the other side.
 *
 * <p>{@code resolution} is the published {@code Resolución} cell — the source's own word for what
 * was decided, such as <em>Adxudicado</em> — and is a different thing from the resolution
 * <em>path</em> beside it, which is this system's record of how the awardee was reached. The
 * stated execution period is held as published text ({@code 12 meses}, {@code 2 meses 7 días}),
 * because it is prose the source writes rather than a duration it publishes.
 *
 * <p>Every published value is nullable and null means the source published nothing there; a value
 * that could not be interpreted arrives here as null and is not a reason to refuse the award. Text
 * that carried only whitespace is held as null rather than as an empty string. The procedure
 * reference is required on every row and a null lote means <em>the procedure as a whole</em>: 85
 * of 100 measured procedures have no lotes, and they are where the ordinary award hangs.
 *
 * <p>{@code withdrawn} is the marker R13's retention needs. Nothing here writes it — the
 * reconciliation that does is a later task's.
 */
@MappedEntity("licitacion_award")
public record Award(
    @Id @GeneratedValue @Nullable AwardId id,
    LicitacionId licitacionId,
    @Nullable LoteId loteId,
    @Nullable String resolution,
    @Nullable LocalDate resolutionDate,
    @Nullable Money amount,
    @Nullable String executionPeriod,
    @Nullable String awardeeName,
    @Nullable OperadorId operadorEconomicoId,
    AwardeeResolutionPath awardeeResolutionPath,
    boolean withdrawn) {

  public Award {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(awardeeResolutionPath, "awardeeResolutionPath must not be null");
    boolean namesNobody = operadorEconomicoId == null;
    boolean noRouteAnswered = awardeeResolutionPath == AwardeeResolutionPath.UNRESOLVED;
    if (namesNobody != noRouteAnswered) {
      throw new IllegalArgumentException(
          "an award names an operador exactly when a route reached one: %s named %s"
              .formatted(awardeeResolutionPath, operadorEconomicoId));
    }
    resolution = PublishedText.orNullWhenBlank(resolution);
    executionPeriod = PublishedText.orNullWhenBlank(executionPeriod);
    awardeeName = PublishedText.orNullWhenBlank(awardeeName);
  }

  /**
   * An award as it is first read from the source: the database assigns its id on insert, and
   * nothing an import does withdraws it.
   */
  public Award(
      LicitacionId licitacionId,
      @Nullable LoteId loteId,
      @Nullable String resolution,
      @Nullable LocalDate resolutionDate,
      @Nullable Money amount,
      @Nullable String executionPeriod,
      @Nullable String awardeeName,
      @Nullable OperadorId operadorEconomicoId,
      AwardeeResolutionPath awardeeResolutionPath) {
    this(
        null,
        licitacionId,
        loteId,
        resolution,
        resolutionDate,
        amount,
        executionPeriod,
        awardeeName,
        operadorEconomicoId,
        awardeeResolutionPath,
        false);
  }

  /**
   * Identity, not contents: two instances are the same award when they carry the same assigned
   * {@link AwardId}. This is the equality a restatement needs — an award whose amount is corrected
   * or whose awardee moves to a different operador is the same award, and the record's own
   * equality would call it a different one.
   *
   * <p>An award the database has not assigned an identity to is equal only to itself. The pair
   * that names its award point identifies it at the source, but two instances holding one are two
   * readings of the same publication, and deciding they are interchangeable is the store's job at
   * upsert.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Award award && id != null && id.equals(award.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
