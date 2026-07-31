package gal.conxugal.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The running instance's datastore, reached directly to remove the accounts a scenario
 * created. The administration API never deletes an account, so this is the only way back to
 * an instance that is free of them.
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
    requireDatastoreOfTheInstanceUnderTest();
    try (Connection connection = DriverManager.getConnection(URL, USERNAME, CREDENTIAL);
        PreparedStatement statement =
            connection.prepareStatement("DELETE FROM users WHERE email = ?")) {
      statement.setString(1, email);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Could not delete the account %s".formatted(email), e);
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
