package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Nut;
import gal.conxugal.domain.licitacion.NutClassification;
import gal.conxugal.domain.licitacion.NutClassificationId;
import gal.conxugal.domain.licitacion.NutClassificationRepository;
import gal.conxugal.domain.licitacion.NutId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores a procedure's NUTS classifications, on {@link JdbcCpvClassificationRepository}'s shape and
 * for its reasons — including the {@code NULLS NOT DISTINCT} key that lets a procedure-wide row
 * match itself across imports.
 *
 * @see JdbcCpvClassificationRepository
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcNutClassificationRepository
    implements NutClassificationRepository,
        GenericRepository<NutClassification, NutClassificationId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_nut (licitacion_id, lote_id, nut_id, diffusion_date, withdrawn)
      VALUES (?::uuid, ?::uuid, ?::uuid, ?::date, ?::boolean)
      ON CONFLICT ON CONSTRAINT licitacion_nut_key DO UPDATE SET
          diffusion_date = EXCLUDED.diffusion_date,
          withdrawn = EXCLUDED.withdrawn
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcNutClassificationRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public NutClassification upsert(NutClassification classification) {
    Objects.requireNonNull(classification, "classification must not be null");
    UUID nutId = identityOf(classification.nut());
    UUID id =
        Upserts.returningId(
            jdbcOperations,
            UPSERT,
            statement -> {
              statement.setObject(1, classification.licitacionId().value());
              statement.setObject(2, Upserts.lote(classification.loteId()));
              statement.setObject(3, nutId);
              statement.setObject(4, Upserts.date(classification.diffusionDate()));
              statement.setBoolean(5, classification.withdrawn());
            });
    return new NutClassification(
        new NutClassificationId(id),
        classification.licitacionId(),
        classification.loteId(),
        classification.nut(),
        classification.diffusionDate(),
        classification.withdrawn());
  }

  private static UUID identityOf(Nut nut) {
    NutId id = nut.id();
    if (id == null) {
      throw new IllegalArgumentException(
          "the NUTS entry must be stored before a classification citing it: %s".formatted(
              nut.code()));
    }
    return id.value();
  }
}
