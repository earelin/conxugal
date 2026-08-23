package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Licitacion;
import gal.conxugal.domain.licitacion.LicitacionContractType;
import gal.conxugal.domain.licitacion.LicitacionContractTypeId;
import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.LicitacionProcedureType;
import gal.conxugal.domain.licitacion.LicitacionProcedureTypeId;
import gal.conxugal.domain.licitacion.LicitacionRepository;
import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateId;
import gal.conxugal.domain.licitacion.LicitacionTramitacionType;
import gal.conxugal.domain.licitacion.LicitacionTramitacionTypeId;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.UpsertOperation;
import gal.conxugal.domain.licitacion.UpsertOutcome;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Stores licitacións. One statement, matching on the publication identifier and refreshing the row
 * in place — never delete-and-reinsert — so a re-imported procedure keeps the identity assigned to
 * it the first time, and every child already hanging off that identity stays attached.
 *
 * <p>{@code xmax} is what tells the two branches apart: PostgreSQL leaves it zero on a row this
 * statement inserted and non-zero on one it updated, so the outcome comes out of the write itself
 * rather than out of a read that would race whoever wrote next.
 *
 * <p><strong>{@code withdrawn} is in neither the insert nor the refresh.</strong> The column is
 * created empty and nothing in this feature writes it: a procedure absent from a later import is
 * retained unchanged, because absence at the source is not evidence of withdrawal, and the explicit
 * removal that <em>is</em> meaningful belongs to a later feature. Refreshing it from the argument
 * would let an import silently un-withdraw whatever that feature had marked.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcLicitacionRepository
    implements LicitacionRepository, GenericRepository<Licitacion, LicitacionId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion (
          publication_id, organo_id, publication_date, last_modified, state_id, expediente,
          obxecto, contract_type_id, procedure_type_id, tramitacion_type_id, lote_count,
          base_budget, estimated_value)
      VALUES (?::text, ?::uuid, ?::date, ?::date, ?::uuid, ?::text, ?::text, ?::uuid, ?::uuid,
              ?::uuid, ?::int, ?::numeric, ?::numeric)
      ON CONFLICT ON CONSTRAINT licitacion_publication_id_key DO UPDATE SET
          organo_id = EXCLUDED.organo_id,
          publication_date = EXCLUDED.publication_date,
          last_modified = EXCLUDED.last_modified,
          state_id = EXCLUDED.state_id,
          expediente = EXCLUDED.expediente,
          obxecto = EXCLUDED.obxecto,
          contract_type_id = EXCLUDED.contract_type_id,
          procedure_type_id = EXCLUDED.procedure_type_id,
          tramitacion_type_id = EXCLUDED.tramitacion_type_id,
          lote_count = EXCLUDED.lote_count,
          base_budget = EXCLUDED.base_budget,
          estimated_value = EXCLUDED.estimated_value
      RETURNING id, (xmax = 0) AS inserted
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcLicitacionRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  /**
   * Declared only to carry the four joins, and they are <strong>what makes this read work at
   * all</strong> rather than a tuning choice. Micronaut Data has no implicit to-one fetch:
   * unjoined, the mapper builds each reference as an id-only stub, which it can only do for an
   * entity whose constructor takes nothing or takes the identity alone. None of these four
   * qualifies, so all four would arrive null — and {@code Licitacion}'s constructor refuses a null
   * state, on every stored row rather than an unlucky one. The three nullable types would have
   * failed silently instead, arriving null with their columns populated.
   *
   * <p><strong>Left</strong> rather than the default inner join, because three of the four are
   * nullable: an inner join would drop any procedure whose record published no contract type from a
   * read that asked for it by its identifier. How often the source omits one is not measured, and
   * the join does not need it to be common to be wrong.
   */
  @Override
  @Join(value = "state", type = Join.Type.LEFT_FETCH)
  @Join(value = "contractType", type = Join.Type.LEFT_FETCH)
  @Join(value = "procedureType", type = Join.Type.LEFT_FETCH)
  @Join(value = "tramitacionType", type = Join.Type.LEFT_FETCH)
  public abstract Optional<Licitacion> findByPublicationId(PublicationId publicationId);

  @Override
  @Transactional
  public UpsertOutcome upsert(Licitacion licitacion) {
    Objects.requireNonNull(licitacion, "licitacion must not be null");
    UUID stateId = identityOf(licitacion.state());
    UUID contractTypeId = identityOf(licitacion.contractType());
    UUID procedureTypeId = identityOf(licitacion.procedureType());
    UUID tramitacionTypeId = identityOf(licitacion.tramitacionType());
    return Upserts.returning(
        jdbcOperations,
        UPSERT,
        statement -> {
          statement.setString(1, licitacion.publicationId().value());
          statement.setObject(2, licitacion.organoId().value());
          statement.setObject(3, Upserts.date(licitacion.publicationDate()));
          statement.setObject(4, Upserts.date(licitacion.lastModified()));
          statement.setObject(5, stateId);
          statement.setString(6, licitacion.expediente());
          statement.setString(7, licitacion.obxecto());
          statement.setObject(8, contractTypeId);
          statement.setObject(9, procedureTypeId);
          statement.setObject(10, tramitacionTypeId);
          statement.setObject(11, licitacion.loteCount());
          statement.setObject(12, Upserts.amount(licitacion.baseBudget()));
          statement.setObject(13, Upserts.amount(licitacion.estimatedValue()));
        },
        rows ->
            new UpsertOutcome(
                new LicitacionId(rows.getObject("id", UUID.class)),
                rows.getBoolean("inserted") ? UpsertOperation.ADDED : UpsertOperation.REFRESHED));
  }

  /**
   * The vocabulary must be stored before the procedure naming it, and a caller that forgot would
   * otherwise send a null into a {@code NOT NULL} foreign key, whose error names the column rather
   * than the mistake. Refused here on the precedent {@code JdbcOperadorRepository.retainName} sets
   * for an unstored operador — the diagnosis belongs where the mistake is.
   */
  private static UUID identityOf(LicitacionState state) {
    LicitacionStateId id = state.id();
    if (id == null) {
      throw new IllegalArgumentException(
          "the state must be stored before a procedure naming it: %d".formatted(state.code()));
    }
    return id.value();
  }

  /** A procedure naming no type at all is the ordinary case, and null is what it stores. */
  private static @Nullable UUID identityOf(@Nullable LicitacionContractType type) {
    if (type == null) {
      return null;
    }
    LicitacionContractTypeId id = type.id();
    if (id == null) {
      throw new IllegalArgumentException(
          "the contract type must be stored before a procedure naming it: %s".formatted(
              type.name()));
    }
    return id.value();
  }

  private static @Nullable UUID identityOf(@Nullable LicitacionProcedureType type) {
    if (type == null) {
      return null;
    }
    LicitacionProcedureTypeId id = type.id();
    if (id == null) {
      throw new IllegalArgumentException(
          "the procedure type must be stored before a procedure naming it: %s".formatted(
              type.name()));
    }
    return id.value();
  }

  private static @Nullable UUID identityOf(@Nullable LicitacionTramitacionType type) {
    if (type == null) {
      return null;
    }
    LicitacionTramitacionTypeId id = type.id();
    if (id == null) {
      throw new IllegalArgumentException(
          "the tramitación type must be stored before a procedure naming it: %s".formatted(
              type.name()));
    }
    return id.value();
  }
}
