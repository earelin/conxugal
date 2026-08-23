package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;

/**
 * Raw SQL against the migrated schema, for every test in this package. The two factories are the
 * whole of the difference between its two uses, and it is a real one:
 *
 * <ul>
 *   <li>the migration tests <strong>commit</strong>, because they violate constraints on purpose —
 *       a failed statement aborts the connection Micronaut Data shares with them, so each has to
 *       stand on its own and be rolled back before the next runs;
 *   <li>the adapter tests <strong>do not</strong>, because they expect every write to succeed and
 *       the adapter under test shares that connection, so it sees them uncommitted.
 * </ul>
 *
 * <p>One class rather than two, because the alternative was two fixtures in one package holding the
 * same {@code organo_contratacion} insert and the same insert-returning-id plumbing, differing in a
 * commit.
 *
 * <p>Every statement is written out rather than assembled, including the three that exist only to
 * be refused: a helper taking SQL from its caller would put an unbounded statement behind a
 * package-private door, and this is a fixture rather than a query API.
 */
final class SchemaFixture {

  private final DataSource dataSource;
  private final boolean committing;

  private SchemaFixture(DataSource dataSource, boolean committing) {
    this.dataSource = dataSource;
    this.committing = committing;
  }

  /** For a test that violates a constraint on purpose, and must leave the connection usable. */
  static SchemaFixture committing(DataSource dataSource) {
    return new SchemaFixture(dataSource, true);
  }

  /** For a test whose writes the adapter under test has to see, on the connection they share. */
  static SchemaFixture joiningTheTestTransaction(DataSource dataSource) {
    return new SchemaFixture(dataSource, false);
  }

  /** The convening Órgano, typed, for the tests that hand it to an adapter. */
  OrganoId organo(String sourceKey) throws SQLException {
    return new OrganoId(insertOrgano(sourceKey));
  }

  /** An operador for an award to name, typed, for the tests that hand it to an adapter. */
  OperadorId operador(String fiscalId, String name) throws SQLException {
    return new OperadorId(
        insertReturningId(
            "INSERT INTO operador_economico (id, fiscal_id, name, name_rank_source_id)"
                + " VALUES (uuidv7(), ?, ?, 4711) RETURNING id",
            fiscalId,
            name));
  }

  /**
   * A stored procedure for a child to hang off, typed, with the Órgano and the state it needs.
   * Written straight rather than through the adapters: what these tests are about is the child, and
   * the procedure beneath it is scenery.
   */
  LicitacionId licitacion(String publicationId) throws SQLException {
    UUID organoId = insertOrgano("consorcio-%s".formatted(publicationId));
    UUID stateId = insertState(2, "Adxudicado");
    return new LicitacionId(insertLicitacion(publicationId, organoId, stateId));
  }

  UUID insertOrgano(String sourceKey) throws SQLException {
    return insertReturningId(
        "INSERT INTO organo_contratacion (id, source_key, name, active)"
            + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id",
        sourceKey,
        sourceKey);
  }

  UUID insertState(int code, String label) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_state (code, label) VALUES (?, ?) RETURNING id", code, label);
  }

  UUID insertContractType(String name) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_contract_type (name) VALUES (?) RETURNING id", name);
  }

  UUID insertProcedureType(String name) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_procedure_type (name) VALUES (?) RETURNING id", name);
  }

  UUID insertCpv(String code, String description) throws SQLException {
    return insertReturningId(
        "INSERT INTO cpv (code, description) VALUES (?, ?) RETURNING id", code, description);
  }

  UUID insertNut(String code, String description) throws SQLException {
    return insertReturningId(
        "INSERT INTO nut (code, description) VALUES (?, ?) RETURNING id", code, description);
  }

  UUID insertLicitacion(String publicationId, UUID organoId, UUID stateId) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion (publication_id, organo_id, state_id) VALUES (?, ?, ?)"
            + " RETURNING id",
        publicationId,
        organoId,
        stateId);
  }

  /** The listing always publishes a state, so this exists only to be refused. */
  UUID insertLicitacionWithoutState(String publicationId, UUID organoId) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion (publication_id, organo_id) VALUES (?, ?) RETURNING id",
        publicationId,
        organoId);
  }

  UUID insertLote(UUID licitacionId, String identifier, String key) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_lote (licitacion_id, lote_identifier, lote_key)"
            + " VALUES (?, ?, ?) RETURNING id",
        licitacionId,
        identifier,
        key);
  }

  UUID insertAward(UUID licitacionId, UUID loteId) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_award (licitacion_id, lote_id, awardee_resolution_path)"
            + " VALUES (?, ?, 'UNRESOLVED') RETURNING id",
        licitacionId,
        loteId);
  }

  /** UNRESOLVED is the value an award nothing resolved carries, so this exists to be refused. */
  UUID insertAwardWithoutResolutionPath(UUID licitacionId) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_award (licitacion_id) VALUES (?) RETURNING id", licitacionId);
  }

  /** Every child carries its procedure, so this exists to be refused. */
  UUID insertAwardWithoutProcedure() throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_award (awardee_resolution_path) VALUES ('UNRESOLVED')"
            + " RETURNING id");
  }

  UUID insertFormalisation(UUID licitacionId, UUID loteId) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_formalisation (licitacion_id, lote_id) VALUES (?, ?) RETURNING id",
        licitacionId,
        loteId);
  }

  UUID insertCpvClassification(UUID licitacionId, UUID loteId, UUID cpvId) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_cpv (licitacion_id, lote_id, cpv_id) VALUES (?, ?, ?)"
            + " RETURNING id",
        licitacionId,
        loteId,
        cpvId);
  }

  UUID insertNutClassification(UUID licitacionId, UUID loteId, UUID nutId) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_nut (licitacion_id, lote_id, nut_id) VALUES (?, ?, ?)"
            + " RETURNING id",
        licitacionId,
        loteId,
        nutId);
  }

  /** Nothing an import does deletes a published value, so this exists to be refused. */
  void deleteState(UUID stateId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("DELETE FROM licitacion_state WHERE id = ?")) {
      statement.setObject(1, stateId);
      statement.executeUpdate();
      commit(connection);
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  void insertLicitacionImportState(
      UUID organoId, String state, @Nullable Integer cursorOffset) throws SQLException {
    executeUpdate(
        "INSERT INTO licitacion_import_state (organo_id, state, cursor_offset)"
            + " VALUES (?, ?, ?)",
        organoId,
        state,
        cursorOffset);
  }

  /** An import that has started always has a status, so this exists only to be refused. */
  void insertLicitacionImportStateWithoutState(UUID organoId) throws SQLException {
    executeUpdate("INSERT INTO licitacion_import_state (organo_id) VALUES (?)", organoId);
  }

  void insertOutstandingRecord(
      UUID organoId,
      String publicationId,
      @Nullable LocalDate publicationDate,
      @Nullable LocalDate lastModified,
      int stateCode,
      @Nullable String stateLabel)
      throws SQLException {
    executeUpdate(
        "INSERT INTO licitacion_outstanding_record (organo_id, publication_id, publication_date,"
            + " last_modified, state_code, state_label) VALUES (?, ?, ?, ?, ?, ?)",
        organoId,
        publicationId,
        publicationDate,
        lastModified,
        stateCode,
        stateLabel);
  }

  /** The listing always publishes a state code, so this exists only to be refused. */
  void insertOutstandingRecordWithoutStateCode(UUID organoId, String publicationId)
      throws SQLException {
    executeUpdate(
        "INSERT INTO licitacion_outstanding_record (organo_id, publication_id) VALUES (?, ?)",
        organoId,
        publicationId);
  }

  /**
   * One Órgano's other-family progress rendered as PostgreSQL renders a whole row: the literal
   * bytes of every column at once, which is what the claim that a licitacións write leaves it
   * unchanged is about. Casting in SQL also keeps the instant out of a JDBC type whose reading
   * depends on the session's zone.
   */
  String contratosMenoresImportStateRowOf(UUID organoId) throws SQLException {
    return queryStrings(
            """
            SELECT progress::text AS whole_row FROM contrato_menor_import_state progress
             WHERE organo_id = ?
            """,
            "whole_row",
            organoId)
        .getFirst();
  }

  /** The same reading of this family's own progress, for the claim in the other direction. */
  String licitacionImportStateRowOf(UUID organoId) throws SQLException {
    return queryStrings(
            """
            SELECT progress::text AS whole_row FROM licitacion_import_state progress
             WHERE organo_id = ?
            """,
            "whole_row",
            organoId)
        .getFirst();
  }

  List<String> columnNamesOf(String table) throws SQLException {
    return queryStrings(
        "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
        "column_name",
        table);
  }

  String columnTypeOf(String table, String column) throws SQLException {
    return queryStrings(
            """
            SELECT data_type FROM information_schema.columns
             WHERE table_name = ? AND column_name = ?
            """,
            "data_type",
            table,
            column)
        .getFirst();
  }

  List<String> indexNamesOf(String table) throws SQLException {
    return queryStrings("SELECT indexname FROM pg_indexes WHERE tablename = ?", "indexname", table);
  }

  List<String> foreignKeyTargetsOf(String table) throws SQLException {
    return queryStrings(
        """
        SELECT target.relname AS target_table
          FROM pg_constraint constraint_
          JOIN pg_class source ON source.oid = constraint_.conrelid
          JOIN pg_class target ON target.oid = constraint_.confrelid
         WHERE source.relname = ?
           AND constraint_.contype = 'f'
        """,
        "target_table",
        table);
  }

  private UUID insertReturningId(String sql, Object... parameters) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        UUID id = rows.getObject("id", UUID.class);
        commit(connection);
        return id;
      }
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  private void executeUpdate(String sql, Object... parameters) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
      commit(connection);
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  private List<String> queryStrings(String sql, String column, Object... parameters)
      throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          values.add(rows.getString(column));
        }
      }
    }
    return values;
  }

  /**
   * Committing is what makes a write outlive the transaction the test method runs in, and the
   * autocommit guard is {@link gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup}'s: a
   * connection that commits as it goes has nothing to commit here and refuses to be asked.
   */
  private void commit(Connection connection) throws SQLException {
    if (committing && !connection.getAutoCommit()) {
      connection.commit();
    }
  }

  /**
   * The injected DataSource is Micronaut Data's connection-context-aware proxy, so every call here
   * shares one underlying connection: a failed statement aborts that connection's transaction, and
   * it must be rolled back before any later statement — including a following test's truncate — can
   * run on it.
   */
  private void rollbackQuietly(SQLException cause) {
    try (Connection connection = dataSource.getConnection()) {
      connection.rollback();
    } catch (SQLException rollbackException) {
      cause.addSuppressed(rollbackException);
    }
  }
}
