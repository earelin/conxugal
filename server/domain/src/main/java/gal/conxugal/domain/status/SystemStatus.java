package gal.conxugal.domain.status;

import static gal.conxugal.commons.validation.Preconditions.requireNotNegative;

import java.time.Instant;
import java.util.Objects;

/**
 * A point-in-time snapshot of overall service health: whether the datastore is reachable, and
 * coarse application/runtime figures. Carries no connection string, credential, host or key.
 */
public record SystemStatus(
    ServiceState status, Datastore datastore, Instant checkedAt, ApplicationInfo application,
    RuntimeInfo runtime) {

  public SystemStatus {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(datastore, "datastore must not be null");
    Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    Objects.requireNonNull(application, "application must not be null");
    Objects.requireNonNull(runtime, "runtime must not be null");
  }

  /** Assesses overall service state from the datastore's reachability. */
  public static SystemStatus assess(
      boolean datastoreReachable, Instant checkedAt, ApplicationInfo application,
      RuntimeInfo runtime) {
    ServiceState status = datastoreReachable ? ServiceState.UP : ServiceState.DEGRADED;
    return new SystemStatus(
        status, new Datastore(datastoreReachable), checkedAt, application, runtime);
  }

  /** Overall service state. */
  public enum ServiceState {
    UP,
    DEGRADED
  }

  /** Whether the datastore is reachable — never how to reach it. */
  public record Datastore(boolean reachable) {}

  /** Coarse build/deployment identity. */
  public record ApplicationInfo(String version, String environment) {

    public ApplicationInfo {
      Objects.requireNonNull(version, "version must not be null");
      Objects.requireNonNull(environment, "environment must not be null");
    }
  }

  /** Coarse JVM/OS figures for the running instance — usage figures only, never how to reach it. */
  public record RuntimeInfo(
      String javaVersion, String javaVendor, long uptimeMillis, long memoryUsedBytes,
      long memoryMaxBytes, String osName, String osArch) {

    public RuntimeInfo {
      Objects.requireNonNull(javaVersion, "javaVersion must not be null");
      Objects.requireNonNull(javaVendor, "javaVendor must not be null");
      requireNotNegative(uptimeMillis, "uptimeMillis");
      requireNotNegative(memoryUsedBytes, "memoryUsedBytes");
      requireNotNegative(memoryMaxBytes, "memoryMaxBytes");
      Objects.requireNonNull(osName, "osName must not be null");
      Objects.requireNonNull(osArch, "osArch must not be null");
    }
  }
}
