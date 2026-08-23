package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * The catalogue rows a procedure and its awards take foreign keys to. Neither insert commits: the
 * adapter tests expect every write to succeed, and the injected {@code DataSource} is Micronaut
 * Data's connection-context-aware proxy, so the adapter under test shares this connection and sees
 * them uncommitted. The refusals live in the two migration tests, which commit for that reason.
 */
final class CatalogueFixture {

  private final DataSource dataSource;

  CatalogueFixture(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  OrganoId organo(String sourceKey) throws SQLException {
    return new OrganoId(
        insertReturningId(
            "INSERT INTO organo_contratacion (id, source_key, name, active)"
                + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id",
            sourceKey,
            sourceKey));
  }

  OperadorId operador(String fiscalId, String name) throws SQLException {
    return new OperadorId(
        insertReturningId(
            "INSERT INTO operador_economico (id, fiscal_id, name, name_rank_source_id)"
                + " VALUES (uuidv7(), ?, ?, 4711) RETURNING id",
            fiscalId,
            name));
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
        return rows.getObject("id", UUID.class);
      }
    }
  }
}
