package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.contrato.ContratoMenor;
import gal.conxugal.domain.contrato.ContratoMenorId;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.Optional;

/**
 * Reads and a save, for the tests only. The port offers neither — the import writes contracts and
 * counts them and nothing more — so these live here rather than widening the adapter with methods
 * no production code would call.
 *
 * <p>The join is not incidental: a contract's awardee is one foreign key, so reading it means
 * joining {@code operador_economico}. Without the join Micronaut Data would fetch the identifier
 * alone and try to build an operador out of it.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface ContratoMenorTestRepository extends GenericRepository<ContratoMenor, ContratoMenorId> {

  ContratoMenor save(ContratoMenor contrato);

  @Join(value = "operadorEconomico", type = Join.Type.LEFT_FETCH)
  Optional<ContratoMenor> findById(ContratoMenorId id);

  @Join(value = "operadorEconomico", type = Join.Type.LEFT_FETCH)
  Optional<ContratoMenor> findBySourceId(long sourceId);
}
