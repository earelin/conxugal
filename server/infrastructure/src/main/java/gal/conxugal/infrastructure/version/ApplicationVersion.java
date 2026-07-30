package gal.conxugal.infrastructure.version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the running application's version from the classpath resource this module's build
 * generates ({@code generateVersionProperties} in {@code infrastructure/build.gradle.kts}), so
 * it's present on every classpath that includes {@code infrastructure}'s {@code main} output —
 * the assembled application as well as this module's own test suites. The fallback below only
 * applies if that generated resource is ever missing.
 */
public final class ApplicationVersion {

  public static final String UNKNOWN = "unknown";

  private static final String VERSION_RESOURCE = "conxugal-version.properties";

  private ApplicationVersion() {}

  public static String read() {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
      if (in == null) {
        return UNKNOWN;
      }
      Properties properties = new Properties();
      properties.load(in);
      return properties.getProperty("version", UNKNOWN);
    } catch (IOException e) {
      return UNKNOWN;
    }
  }
}
