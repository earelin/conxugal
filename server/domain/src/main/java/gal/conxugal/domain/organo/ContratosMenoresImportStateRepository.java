package gal.conxugal.domain.organo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Port for an Órgano's contratos menores import state. Implemented by the {@code infrastructure}
 * module.
 *
 * <p><strong>Nothing here writes {@code coveredThrough}</strong>, and the absence is the point
 * rather than an omission. T₀ is stamped by {@link #insert} when the state row is created and must
 * survive every later resumption unchanged; an update that re-stamped it would put everything
 * published between the first attempt and that resumption outside every future incremental window.
 * A port with no such method is what stops that being written by accident.
 *
 * <p>There is no delete either: an Órgano that has been loaded stays loaded, and unmarking it
 * retains both its contracts and the record of how far they got.
 *
 * <p><strong>Both writes commit in a transaction of their own</strong>, whatever the caller is
 * inside. An import's contracts and the cursor describing them are deliberately not one act — a
 * cursor rolled back by a data failure, or contracts rolled back by a failure to move the cursor,
 * are both the coupling the resumption design exists to avoid.
 */
public interface ContratosMenoresImportStateRepository {

  Optional<ContratosMenoresImportState> findByOrganoId(OrganoId organoId);

  @Insert
  ContratosMenoresImportState insert(ContratosMenoresImportState state);

  /** Moves the resumption point, touching neither the status nor T₀. */
  void updateCursorDate(@Id OrganoId organoId, @Nullable LocalDate cursorDate);

  /** Moves the status, touching neither the cursor nor T₀. */
  void updateState(@Id OrganoId organoId, ContratosMenoresImportStatus state);
}
