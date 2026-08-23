package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionOutstandingRecord;
import gal.conxugal.domain.licitacion.LicitacionOutstandingRecordRepository;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * A caller with a transaction of its own, so the ledger's propagation can be driven rather than
 * described. Its test runs outside a transaction, which is what the two writes' propagations are
 * about — and outside one they are indistinguishable, since either opens a transaction and commits.
 *
 * <p>A DI-managed bean rather than a plain instance, because the propagation under test is
 * interceptor advice: constructing this directly would run the adapter with no ambient transaction
 * at all and pass whatever the annotations said.
 */
@Singleton
class LedgerCaller {

  private final LicitacionOutstandingRecordRepository ledger;

  LedgerCaller(LicitacionOutstandingRecordRepository ledger) {
    this.ledger = ledger;
  }

  /** What the walk does when a procedure's own transaction is about to be lost. */
  @Transactional
  void recordThenFail(OrganoId organoId, LicitacionOutstandingRecord outstanding) {
    ledger.record(organoId, outstanding);
    throw new IllegalStateException("the procedure this entry describes could not be stored");
  }

  /** What the walk does when a retried procedure is stored and then one of its children is not. */
  @Transactional
  void clearThenFail(OrganoId organoId, PublicationId publicationId) {
    ledger.clear(organoId, publicationId);
    throw new IllegalStateException("a child of the retried procedure could not be stored");
  }

  /** The completion gate, asked from inside a transaction that has cleared but not settled. */
  @Transactional
  boolean clearThenAskWhetherAnythingIsOutstanding(OrganoId organoId, PublicationId publicationId) {
    ledger.clear(organoId, publicationId);
    return ledger.hasOutstanding(organoId);
  }
}
