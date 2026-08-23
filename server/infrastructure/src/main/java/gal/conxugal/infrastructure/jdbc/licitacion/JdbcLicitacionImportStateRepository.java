package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionImportState;
import gal.conxugal.domain.licitacion.LicitacionImportStateRepository;
import gal.conxugal.domain.licitacion.LicitacionImportStatus;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import org.jspecify.annotations.Nullable;

/**
 * Every query here is derived from its own name; the two writes are declared only to carry their
 * propagation.
 *
 * <p>They take a transaction of their own, whatever the caller is inside, because the walk's cursor
 * and the procedures it describes must not commit or roll back together. Without it these would
 * join an ambient transaction and the cursor would be undone by a data failure while the run's
 * progress — which forces its own — survived, leaving the two halves of one advance disagreeing.
 *
 * <p>It touches nothing the other contract family stores. Its table is its own, and the derived
 * queries can only name it.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcLicitacionImportStateRepository
    implements LicitacionImportStateRepository,
        GenericRepository<LicitacionImportState, OrganoId> {

  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public abstract void updateCursorOffset(@Id OrganoId organoId, @Nullable Integer cursorOffset);

  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public abstract void updateState(@Id OrganoId organoId, LicitacionImportStatus state);
}
