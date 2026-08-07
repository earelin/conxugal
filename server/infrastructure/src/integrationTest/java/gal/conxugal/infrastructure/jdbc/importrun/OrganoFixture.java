package gal.conxugal.infrastructure.jdbc.importrun;

import gal.conxugal.domain.organo.OrganoId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * An Órgano for a run to cover. Every coverage row takes a foreign key to one, so the tests here
 * need a real catalogue row before they can claim anything, and none of them care what is in it.
 *
 * <p>It takes the connection rather than finding one, because these tests drive raw connections
 * off the container: what they set up has to be committed and visible to the threads and the
 * transactions the adapter opens for itself.
 */
final class OrganoFixture {

  private OrganoFixture() {
  }

  static OrganoId insert(Connection connection, String sourceKey) throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active)"
            + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, sourceKey);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }
}
