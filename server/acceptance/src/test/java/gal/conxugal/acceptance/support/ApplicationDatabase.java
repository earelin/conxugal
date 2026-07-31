package gal.conxugal.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The running instance's datastore, reached directly to undo what a scenario wrote. The
 * administration API never deletes an account, so this is the only way a suite can leave the
 * instance as it found it.
 */
public final class ApplicationDatabase {

  private static final String URL =
      System.getProperty("db.url", "jdbc:postgresql://localhost:5432/conxugal");
  private static final String USERNAME = System.getProperty("db.username", "conxugal");
  private static final String CREDENTIAL = System.getProperty("db.password", "conxugal");

  private ApplicationDatabase() {
  }

  /** Removes every account whose email matches the SQL {@code LIKE} pattern. */
  public static void deleteAccounts(String emailPattern) {
    try (Connection connection = DriverManager.getConnection(URL, USERNAME, CREDENTIAL);
        PreparedStatement statement =
            connection.prepareStatement("DELETE FROM users WHERE email LIKE ?")) {
      statement.setString(1, emailPattern);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Could not delete the accounts matching %s".formatted(emailPattern), e);
    }
  }
}
