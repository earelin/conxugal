package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Cpv;
import gal.conxugal.domain.licitacion.CpvClassification;
import gal.conxugal.domain.licitacion.CpvClassificationId;
import gal.conxugal.domain.licitacion.CpvClassificationRepository;
import gal.conxugal.domain.licitacion.CpvId;
import gal.conxugal.domain.licitacion.LicitacionId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores a procedure's CPV classifications. One statement, keyed on the procedure, the lote and the
 * entry cited.
 *
 * <p>The key's {@code NULLS NOT DISTINCT} is what makes a procedure-wide row — {@code lote_id} null
 * — match itself across imports. Without it PostgreSQL would treat every reading of that row as
 * distinct from the last and insert it afresh on every run, which is every lotless procedure, which
 * is most of them.
 *
 * <p>Neither the procedure nor the lote nor the entry is in the refresh list: all three are the
 * key, and a row whose key changed is a different classification rather than a correction of this
 * one.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcCpvClassificationRepository
    implements CpvClassificationRepository,
        GenericRepository<CpvClassification, CpvClassificationId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_cpv (licitacion_id, lote_id, cpv_id, diffusion_date, withdrawn)
      VALUES (?::uuid, ?::uuid, ?::uuid, ?::date, ?::boolean)
      ON CONFLICT ON CONSTRAINT licitacion_cpv_key DO UPDATE SET
          diffusion_date = EXCLUDED.diffusion_date,
          withdrawn = EXCLUDED.withdrawn
      RETURNING id
      """;

  private static final String WITHDRAW_ABSENT = Withdrawals.statementFor("licitacion_cpv");

  private final JdbcOperations jdbcOperations;

  protected JdbcCpvClassificationRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public CpvClassification upsert(CpvClassification classification) {
    Objects.requireNonNull(classification, "classification must not be null");
    UUID cpvId = identityOf(classification.cpv());
    UUID id =
        Upserts.returningId(
            jdbcOperations,
            UPSERT,
            statement -> {
              statement.setObject(1, classification.licitacionId().value());
              statement.setObject(2, Upserts.lote(classification.loteId()));
              statement.setObject(3, cpvId);
              statement.setObject(4, Upserts.date(classification.diffusionDate()));
              statement.setBoolean(5, classification.withdrawn());
            });
    return new CpvClassification(
        new CpvClassificationId(id),
        classification.licitacionId(),
        classification.loteId(),
        classification.cpv(),
        classification.diffusionDate(),
        classification.withdrawn());
  }

  @Override
  @Transactional
  public int withdrawAbsent(
      LicitacionId licitacionId, Collection<CpvClassificationId> retained) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(retained, "retained must not be null");
    return Withdrawals.absent(
        jdbcOperations, WITHDRAW_ABSENT, licitacionId, retained, CpvClassificationId::value);
  }

  /**
   * The entry must be stored before the classification citing it, and a caller that forgot would
   * otherwise send a null into a {@code NOT NULL} foreign key whose error names the column rather
   * than the mistake — {@code JdbcOperadorRepository.retainName}'s precedent.
   */
  private static UUID identityOf(Cpv cpv) {
    CpvId id = cpv.id();
    if (id == null) {
      throw new IllegalArgumentException(
          "the CPV entry must be stored before a classification citing it: %s".formatted(
              cpv.code()));
    }
    return id.value();
  }
}
