package gal.conxugal.application.rest.admin.status;

import gal.conxugal.domain.status.SystemStatus;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/** The current system status — never carries a connection string, credential, host or key. */
@Serdeable
public record SystemStatusResponse(
    SystemStatus.ServiceState status, Datastore datastore, Instant checkedAt,
    ApplicationInfo application, RuntimeInfo runtime) {

  @Serdeable
  public record Datastore(boolean reachable) {}

  @Serdeable
  public record ApplicationInfo(String version, String environment) {}

  @Serdeable
  public record RuntimeInfo(
      String javaVersion, String javaVendor, long uptimeMillis, long memoryUsedBytes,
      long memoryMaxBytes, String osName, String osArch) {}

  static SystemStatusResponse of(SystemStatus status) {
    return new SystemStatusResponse(
        status.status(),
        new Datastore(status.datastore().reachable()),
        status.checkedAt(),
        new ApplicationInfo(status.application().version(), status.application().environment()),
        new RuntimeInfo(
            status.runtime().javaVersion(),
            status.runtime().javaVendor(),
            status.runtime().uptimeMillis(),
            status.runtime().memoryUsedBytes(),
            status.runtime().memoryMaxBytes(),
            status.runtime().osName(),
            status.runtime().osArch()));
  }
}
