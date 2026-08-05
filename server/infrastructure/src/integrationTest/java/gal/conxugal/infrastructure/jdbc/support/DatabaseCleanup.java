package gal.conxugal.infrastructure.jdbc.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Empties every table between tests, so no test class carries a list of the tables it happens to
 * touch. Such a list is only correct until someone adds a foreign key to one of them: a table
 * referenced by a new one can no longer be truncated on its own, so the omission surfaces as an
 * unrelated suite failing rather than as the new table's own test.
 *
 * <p>The database is asked which tables exist rather than being told, which is why this is one
 * constant statement and not SQL assembled around a name list. Flyway's history is left alone —
 * truncating it would make every migration run a second time on the next connection.
 */
public final class DatabaseCleanup {

  private static final String TRUNCATE_EVERY_TABLE =
      """
      DO $$
      DECLARE
          tables text;
      BEGIN
          SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
            INTO tables
            FROM pg_tables
           WHERE schemaname = 'public'
             AND tablename <> 'flyway_schema_history';
          IF tables IS NOT NULL THEN
              EXECUTE 'TRUNCATE TABLE ' || tables;
          END IF;
      END $$;
      """;

  private DatabaseCleanup() {
  }

  /** Empties every table, on a connection borrowed for the purpose. */
  public static void truncateAllTables(DataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      truncateAllTables(connection);
    }
  }

  /**
   * Empties every table on the given connection, committing when that connection is one a test
   * drives itself. A raw connection off the container commits as it goes and has nothing to
   * commit here; the injected {@code DataSource}'s is Micronaut Data's connection-context-aware
   * proxy, shared with the code under test, where the commit is what makes the emptying outlive
   * the transaction the test method runs in.
   */
  public static void truncateAllTables(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(TRUNCATE_EVERY_TABLE);
    }
    if (!connection.getAutoCommit()) {
      connection.commit();
    }
  }
}
