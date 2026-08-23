package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Formalisation;
import gal.conxugal.domain.licitacion.FormalisationId;
import gal.conxugal.domain.licitacion.FormalisationRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores a procedure's formalisations, on {@link JdbcAwardRepository}'s shape: keyed on the award
 * point, with {@code withdrawn} refreshed for the same reason.
 *
 * <p>Its own table and its own statement rather than columns on the award, because the two are
 * separate publications that can disagree — a procedure can be awarded without being formalised,
 * and the two can name different parties.
 *
 * <p>{@code fiscal_identifier} is nullable and stays that way: what this row holds is the
 * identifier the {@code Contratista} cell carried, and a cell whose trailing token was not
 * identifier-shaped carried none. It is not the catalogue's key and does not have to resolve to
 * anybody.
 *
 * @see JdbcAwardRepository
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcFormalisationRepository
    implements FormalisationRepository, GenericRepository<Formalisation, FormalisationId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_formalisation (
          licitacion_id, lote_id, formalisation_date, contratista_name, fiscal_identifier,
          nationality, amount, withdrawn)
      VALUES (?::uuid, ?::uuid, ?::date, ?::text, ?::text, ?::text, ?::numeric, ?::boolean)
      ON CONFLICT ON CONSTRAINT licitacion_formalisation_key DO UPDATE SET
          formalisation_date = EXCLUDED.formalisation_date,
          contratista_name = EXCLUDED.contratista_name,
          fiscal_identifier = EXCLUDED.fiscal_identifier,
          nationality = EXCLUDED.nationality,
          amount = EXCLUDED.amount,
          withdrawn = EXCLUDED.withdrawn
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcFormalisationRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public Formalisation upsert(Formalisation formalisation) {
    Objects.requireNonNull(formalisation, "formalisation must not be null");
    UUID id =
        Upserts.returningId(
            jdbcOperations,
            UPSERT,
            statement -> {
              statement.setObject(1, formalisation.licitacionId().value());
              statement.setObject(2, Upserts.lote(formalisation.loteId()));
              statement.setObject(3, Upserts.date(formalisation.formalisationDate()));
              statement.setString(4, formalisation.contratistaName());
              statement.setString(5, Upserts.fiscalIdentifier(formalisation.fiscalIdentifier()));
              statement.setString(6, formalisation.nationality());
              statement.setObject(7, Upserts.amount(formalisation.amount()));
              statement.setBoolean(8, formalisation.withdrawn());
            });
    return new Formalisation(
        new FormalisationId(id),
        formalisation.licitacionId(),
        formalisation.loteId(),
        formalisation.formalisationDate(),
        formalisation.contratistaName(),
        formalisation.fiscalIdentifier(),
        formalisation.nationality(),
        formalisation.amount(),
        formalisation.withdrawn());
  }
}
