package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How far one Órgano's licitacións history has been loaded, and where a resumption picks it up.
 *
 * <p>A value inside the Órgano rather than an entity of its own: {@code organoId} is the table's
 * key, not the value's, so two readings of the same unchanged progress are equal and a reading
 * taken before an advance differs from one taken after.
 *
 * <p>It lives with the Órgano rather than with the run that advanced it, so that removing old run
 * records cannot strand a half-loaded Órgano with nowhere to resume from. Its own table rather than
 * columns on the catalogue row, which is read by every catalogue read while this one is rewritten
 * after every page for hours.
 *
 * <p><strong>The cursor is the offset already consumed</strong>, and it is a conservative hint
 * rather than a ledger: it is written after a page's procedures commit, so a crash between the two
 * leaves it behind what is stored and the resumption re-reads that overlap. Re-reading is safe
 * because storing a procedure again refreshes it in place. It is null until the first page commits.
 *
 * <p>It holds no instant its history is covered through, unlike the other contract family's state.
 * That instant exists there because that family's incremental window is measured from it; this
 * family's incremental mode is driven by the listing's last-modified ordering instead.
 */
@MappedEntity("licitacion_import_state")
public record LicitacionImportState(
    @Id OrganoId organoId, LicitacionImportStatus state, @Nullable Integer cursorOffset) {

  public LicitacionImportState {
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(state, "state must not be null");
  }

  /**
   * The state an Órgano takes when its initial import begins: incomplete, and with no page
   * consumed yet.
   */
  public static LicitacionImportState started(OrganoId organoId) {
    return new LicitacionImportState(organoId, LicitacionImportStatus.INCOMPLETE, null);
  }

  /** The mode an Órgano in this state takes on its next import. */
  public LicitacionImportMode mode() {
    return LicitacionImportMode.of(state);
  }
}
