package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.organo.OrganoId;
import java.util.List;

/**
 * Port for one Órgano's outstanding-record ledger. Implemented by the {@code infrastructure}
 * module.
 *
 * <p>A set of identifiers the next run tries once more, not a retry queue: nothing here counts
 * attempts, delays one or asks when the next is due. That machinery answers a problem nobody has
 * measured, and until one is measured a failing record costs one row.
 *
 * <p>{@link #record} is therefore idempotent by key. The walk meets the same failing record on
 * every run and must not accumulate entries for it; a repeat refreshes what the listing now
 * publishes about it, since that is what a retry will store.
 *
 * <p>{@link #hasOutstanding} exists because completion is gated on it: an Órgano whose listing is
 * exhausted becomes complete only if nothing is outstanding, and otherwise stays incomplete, which
 * is honest and is what keeps it resumable.
 */
public interface LicitacionOutstandingRecordRepository {

  /**
   * Files a record against an Órgano as still to be retrieved, or refreshes the entry already
   * filed for it.
   */
  void record(OrganoId organoId, LicitacionOutstandingRecord outstanding);

  /** Everything still outstanding for an Órgano, in no particular order. */
  List<LicitacionOutstandingRecord> outstandingFor(OrganoId organoId);

  /** Drops one entry, once the procedure it named has been stored. Silent where there is none. */
  void clear(OrganoId organoId, PublicationId publicationId);

  /** Whether an Órgano has anything left to come back to. */
  boolean hasOutstanding(OrganoId organoId);
}
