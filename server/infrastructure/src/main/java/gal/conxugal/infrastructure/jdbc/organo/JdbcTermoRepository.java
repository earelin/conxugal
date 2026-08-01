package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.Termo;
import gal.conxugal.domain.organo.TermoRepository;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Every method but one derives from the port's own names. {@code findAllOrderByName} needs no
 * explicit {@code COLLATE} either — {@code termo.name} is declared with the {@code galician}
 * collation, so the derived {@code ORDER BY} inherits it.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcTermoRepository
    extends TermoRepository, GenericRepository<Termo, UUID> {

  /** The one query that cannot derive — see the port for why. */
  @Override
  @Query("SELECT * FROM termo WHERE parent_id IS NOT DISTINCT FROM :parentId")
  List<Termo> findByParentId(@Nullable UUID parentId);
}
