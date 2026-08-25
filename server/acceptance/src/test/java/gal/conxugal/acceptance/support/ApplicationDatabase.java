package gal.conxugal.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The running instance's datastore, reached directly to undo what a scenario wrote. The
 * administration API never deletes an account and refuses to leave the instance without an
 * enabled administrator, so this is the only way back on both counts.
 */
public final class ApplicationDatabase {

  private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/conxugal";

  private static final String URL = System.getProperty("db.url", DEFAULT_URL);
  private static final String USERNAME = System.getProperty("db.username", "conxugal");
  private static final String CREDENTIAL = System.getProperty("db.password", "conxugal");

  private ApplicationDatabase() {
  }

  /** Removes the account with exactly this email, if the instance still holds one. */
  public static void deleteAccount(String email) {
    updateAccount("DELETE FROM users WHERE email = ?", email, "delete");
  }

  /**
   * Re-enables the account with exactly this email. A scenario that drives the last-enabled-admin
   * refusal has no way back through the API if that guard ever regresses — the instance would be
   * left with no enabled administrator, and no endpoint can restore one — so it repairs the row
   * here instead of leaving every later run locked out.
   */
  public static void enableAccount(String email) {
    updateAccount("UPDATE users SET enabled = TRUE WHERE email = ?", email, "enable");
  }

  /**
   * Private, and every caller hands it a literal: SpotBugs reads a statement built from a
   * parameter of a reachable method as an injection and fails the build, which is why the two
   * writes above name their own SQL rather than a caller naming it for them.
   */
  private static void updateAccount(String sql, String email, String attempted) {
    requireDatastoreOfTheInstanceUnderTest();
    try (Connection connection = DriverManager.getConnection(URL, USERNAME, CREDENTIAL);
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, email);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Could not %s the account %s".formatted(attempted, email), e);
    }
  }

  /**
   * The base URL and the JDBC URL are supplied separately, so nothing stops a run from
   * driving a remote instance while cleaning up the local datastore — writing to a database
   * the scenario never touched, and leaving the remote one to accumulate accounts. Neither
   * shows up as a failed assertion, so refuse the run instead.
   */
  private static void requireDatastoreOfTheInstanceUnderTest() {
    if (DEFAULT_URL.equals(URL)
        && !ApplicationUnderTest.DEFAULT_BASE_URI.equals(ApplicationUnderTest.BASE_URI)) {
      throw new IllegalStateException(
          ("app.baseUrl is %s but db.url is still the local default; pass db.url, db.username "
              + "and db.password for that instance's datastore")
              .formatted(ApplicationUnderTest.BASE_URI));
    }
  }
}
