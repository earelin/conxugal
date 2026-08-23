package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionOutstandingRecord;
import gal.conxugal.domain.licitacion.LicitacionOutstandingRecordRepository;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The ledger's adapter. Written out rather than derived, because the key is the Órgano and the
 * publication together and the entry is filed under an Órgano the value it holds does not name.
 *
 * <p><strong>The two writes take different transactions, and the difference is the point.</strong>
 * Filing an entry forces one of its own: it exists precisely to outlive the failure that rolled the
 * procedure back, and joining that transaction would roll the entry back with it, losing the record
 * of the very failure it describes. Dropping one joins the caller's: an entry stops being
 * outstanding exactly when the procedure it named is stored, so the two must settle together — a
 * drop of its own would leave nothing to come back to for a procedure a later rollback never
 * stored.
 *
 * <p>A plain bean rather than a repository interface Micronaut Data implements, unlike its
 * neighbours: there is no query here to derive from a method name — every statement names a key of
 * two columns, one of which the value it stores does not carry. That is also why the two reads
 * carry a propagation of their own: nothing generated wraps them in a connection.
 */
@Singleton
public class JdbcLicitacionOutstandingRecordRepository
    implements LicitacionOutstandingRecordRepository {

  private static final String RECORD =
      """
      INSERT INTO licitacion_outstanding_record (
          organo_id, publication_id, publication_date, last_modified, state_code, state_label)
      VALUES (?::uuid, ?::text, ?::date, ?::date, ?::int, ?::text)
      ON CONFLICT (organo_id, publication_id) DO UPDATE SET
          publication_date = EXCLUDED.publication_date,
          last_modified = EXCLUDED.last_modified,
          state_code = EXCLUDED.state_code,
          state_label = EXCLUDED.state_label
      """;

  private static final String OUTSTANDING_FOR =
      """
      SELECT publication_id, publication_date, last_modified, state_code, state_label
        FROM licitacion_outstanding_record
       WHERE organo_id = ?
      """;

  private static final String CLEAR =
      """
      DELETE FROM licitacion_outstanding_record
       WHERE organo_id = ? AND publication_id = ?
      """;

  private static final String HAS_OUTSTANDING =
      """
      SELECT EXISTS (
          SELECT 1 FROM licitacion_outstanding_record WHERE organo_id = ?) AS outstanding
      """;

  private final JdbcOperations jdbcOperations;

  public JdbcLicitacionOutstandingRecordRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public void record(OrganoId organoId, LicitacionOutstandingRecord outstanding) {
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(outstanding, "outstanding must not be null");
    jdbcOperations.prepareStatement(RECORD, statement -> {
      statement.setObject(1, organoId.value());
      statement.setString(2, outstanding.publicationId().value());
      statement.setObject(3, Upserts.date(outstanding.publicationDate()));
      statement.setObject(4, Upserts.date(outstanding.lastModified()));
      statement.setInt(5, outstanding.stateCode());
      statement.setString(6, outstanding.stateLabel());
      return statement.executeUpdate();
    });
  }

  @Override
  @Transactional(readOnly = true)
  public List<LicitacionOutstandingRecord> outstandingFor(OrganoId organoId) {
    Objects.requireNonNull(organoId, "organoId must not be null");
    return jdbcOperations.prepareStatement(OUTSTANDING_FOR, statement -> {
      statement.setObject(1, organoId.value());
      try (ResultSet rows = statement.executeQuery()) {
        List<LicitacionOutstandingRecord> outstanding = new ArrayList<>();
        while (rows.next()) {
          outstanding.add(entryOf(rows));
        }
        return outstanding;
      }
    });
  }

  @Override
  @Transactional
  public void clear(OrganoId organoId, PublicationId publicationId) {
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(publicationId, "publicationId must not be null");
    jdbcOperations.prepareStatement(CLEAR, statement -> {
      statement.setObject(1, organoId.value());
      statement.setString(2, publicationId.value());
      return statement.executeUpdate();
    });
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasOutstanding(OrganoId organoId) {
    Objects.requireNonNull(organoId, "organoId must not be null");
    return jdbcOperations.prepareStatement(HAS_OUTSTANDING, statement -> {
      statement.setObject(1, organoId.value());
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getBoolean("outstanding");
      }
    });
  }

  private static LicitacionOutstandingRecord entryOf(ResultSet rows) throws SQLException {
    return new LicitacionOutstandingRecord(
        new PublicationId(rows.getString("publication_id")),
        dateAt(rows, "publication_date"),
        dateAt(rows, "last_modified"),
        rows.getInt("state_code"),
        rows.getString("state_label"));
  }

  private static @Nullable LocalDate dateAt(ResultSet rows, String column) throws SQLException {
    Date stored = rows.getDate(column);
    return stored == null ? null : stored.toLocalDate();
  }
}
