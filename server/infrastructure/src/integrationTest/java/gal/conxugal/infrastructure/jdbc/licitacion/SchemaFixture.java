package gal.conxugal.infrastructure.jdbc.licitacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Raw SQL against the migrated schema, for the two migration tests. Every write commits, because
 * those tests violate constraints on purpose: a failed statement aborts the connection Micronaut
 * Data shares with them, so each has to stand on its own and be rolled back before the next runs.
 *
 * <p>Shared between the procedure's schema test and its children's, because the children all need a
 * stored procedure to hang off and a second copy of these inserts would drift from the first.
 *
 * <p>Every statement is written out rather than assembled, including the four that exist only to be
 * refused: a helper taking SQL from its caller would put an unbounded statement behind a
 * package-private door, and these are fixtures rather than a query API.
 */
final class SchemaFixture {

  private final DataSource dataSource;

  SchemaFixture(DataSource dataSource) {
    this.dataSource = dataSource;
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

  UUID insertTramitacionType(String name) throws SQLException {
    return insertReturningId(
        "INSERT INTO licitacion_tramitacion_type (name) VALUES (?) RETURNING id", name);
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
      connection.commit();
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  List<String> columnNamesOf(String table) throws SQLException {
    return queryStrings(
        "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
        "column_name",
        table);
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
        connection.commit();
        return id;
      }
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  private List<String> queryStrings(String sql, String column, Object parameter)
      throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, parameter);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          values.add(rows.getString(column));
        }
      }
    }
    return values;
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
