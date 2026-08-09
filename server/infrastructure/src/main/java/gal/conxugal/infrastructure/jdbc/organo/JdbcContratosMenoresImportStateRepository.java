package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Every query here is derived from its own name; the two writes are declared only to carry their
 * propagation.
 *
 * <p>They take a transaction of their own, whatever the caller is inside, because the walk's cursor
 * and the contracts it describes must not commit or roll back together. Without it these would join
 * an ambient transaction and the cursor would be undone by a data failure while the run's progress
 * — which forces its own — survived, leaving the two halves of one advance disagreeing.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcContratosMenoresImportStateRepository
    implements ContratosMenoresImportStateRepository,
        GenericRepository<ContratosMenoresImportState, OrganoId> {

  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public abstract void updateCursorDate(@Id OrganoId organoId, @Nullable LocalDate cursorDate);

  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public abstract void updateState(@Id OrganoId organoId, ContratosMenoresImportStatus state);
}
