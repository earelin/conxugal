package gal.conxugal.domain.organo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How far one Órgano's contratos menores history has been loaded, and where a resumption picks it
 * up.
 *
 * <p>This is a <b>value inside the {@link OrganoDeContratacion} aggregate, not an entity of its
 * own</b>: an Órgano has exactly one, nothing distinguishes two of them, and it is reached only
 * through the Órgano it belongs to. {@code organoId} is the column that files the row under that
 * Órgano — it is the table's key, not the value's — so this record holds no identity and compares
 * by its contents, which is what makes a state read before an advance differ from the one read
 * after.
 *
 * <p>It sits in its own table rather than in three more columns on {@code organo_contratacion}
 * because the catalogue row is update-in-place territory for reconciliation and is read by every
 * catalogue read, while this one is rewritten after every batch for days. Separating them keeps
 * that churn off the row the import mark must survive on.
 *
 * <p>These facts live with the Órgano rather than with the import run that produced them, because
 * run history is pruned: an Órgano whose initial import was interrupted has no successful run to
 * protect its rows, so a cursor stored on a run would be pruned with it and leave a half-loaded
 * Órgano with nowhere to resume from — a multi-day walk to redo at one request per second.
 *
 * <p>{@code coveredThrough} is <b>T₀</b> — when the initial import's <em>first</em> window was
 * taken — stamped once, at creation, then carried unchanged across every resumption. Under a
 * newest-first walk an initial import covers {@code [cursorDate, T₀]}, and one spanning several
 * resumptions has several run starts; measuring a future incremental window from the latest of
 * them would leave everything published between the first attempt and that resumption outside
 * every future window, reachable only by a historical re-read that no trigger selects. Nothing
 * here or on the repository can rewrite it, which is what keeps that off-by-one from being
 * available to make.
 *
 * <p>{@code cursorDate} is a conservative hint rather than a ledger: it is written after a batch
 * commits, so a crash in between leaves it slightly behind what is stored and the resumption
 * re-reads that overlap. That is safe because storing a contract again refreshes it in place.
 */
@MappedEntity("contrato_menor_import_state")
public record ContratosMenoresImportState(
    @Id OrganoId organoId,
    ContratosMenoresImportStatus state,
    @Nullable LocalDate cursorDate,
    Instant coveredThrough) {

  public ContratosMenoresImportState {
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(coveredThrough, "coveredThrough must not be null");
  }

  /**
   * The state an Órgano's import starts in: incomplete, stamped with T₀, and with no cursor yet
   * because no window has been walked.
   */
  public static ContratosMenoresImportState startedAt(OrganoId organoId, Instant coveredThrough) {
    return new ContratosMenoresImportState(
        organoId, ContratosMenoresImportStatus.INCOMPLETE, null, coveredThrough);
  }

  /** The mode an Órgano in this state takes on its next import. */
  public ContratosMenoresImportMode mode() {
    return ContratosMenoresImportMode.of(state);
  }
}
