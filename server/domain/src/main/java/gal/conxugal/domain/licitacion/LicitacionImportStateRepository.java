package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Port for an Órgano's licitacións import state. Implemented by the {@code infrastructure} module.
 *
 * <p>An Órgano with no row has never had its licitacións loaded, and {@link #findByOrganoId}
 * answering nothing is how that is read. Nothing here creates a row for an Órgano that is not being
 * imported, so the absence stays meaningful.
 *
 * <p>There is no delete: an Órgano that has been loaded stays loaded, and unmarking it retains both
 * its procedures and the record of how far they got.
 *
 * <p><strong>The two writes move one thing each.</strong> Moving the cursor cannot turn a
 * resumption into a completion, and completing cannot silently rewind the cursor — which is what a
 * single write taking the whole state would let a caller do by passing a value it had read before
 * the last page committed.
 *
 * <p><strong>Both commit in a transaction of their own</strong>, whatever the caller is inside. An
 * import's procedures and the cursor describing them are deliberately not one act: a cursor rolled
 * back by a data failure, or procedures rolled back by a failure to move the cursor, are both the
 * coupling the resumption design exists to avoid.
 */
public interface LicitacionImportStateRepository {

  Optional<LicitacionImportState> findByOrganoId(OrganoId organoId);

  @Insert
  LicitacionImportState insert(LicitacionImportState state);

  /** Moves the resumption point, leaving the status alone. */
  void updateCursorOffset(@Id OrganoId organoId, @Nullable Integer cursorOffset);

  /** Moves the status, leaving the cursor alone. */
  void updateState(@Id OrganoId organoId, LicitacionImportStatus state);
}
