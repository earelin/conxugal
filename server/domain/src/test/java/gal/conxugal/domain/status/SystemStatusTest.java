package gal.conxugal.domain.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SystemStatusTest {

  private static final Instant CHECKED_AT = Instant.parse("2026-07-18T09:30:00Z");
  private static final SystemStatus.ApplicationInfo APPLICATION =
      new SystemStatus.ApplicationInfo("0.1.0-SNAPSHOT", "production");
  private static final SystemStatus.RuntimeInfo RUNTIME =
      new SystemStatus.RuntimeInfo(
          "21.0.3", "Eclipse Adoptium", 266320000L, 536870912L, 1073741824L, "Linux", "amd64");

  @Test
  void assesses_up_when_the_datastore_is_reachable() {
    SystemStatus status = SystemStatus.assess(true, CHECKED_AT, APPLICATION, RUNTIME);

    assertThat(status.status()).isEqualTo(SystemStatus.ServiceState.UP);
    assertThat(status.datastore().reachable()).isTrue();
    assertThat(status.checkedAt()).isEqualTo(CHECKED_AT);
    assertThat(status.application()).isEqualTo(APPLICATION);
    assertThat(status.runtime()).isEqualTo(RUNTIME);
  }

  @Test
  void assesses_degraded_when_the_datastore_is_unreachable() {
    SystemStatus status = SystemStatus.assess(false, CHECKED_AT, APPLICATION, RUNTIME);

    assertThat(status.status()).isEqualTo(SystemStatus.ServiceState.DEGRADED);
    assertThat(status.datastore().reachable()).isFalse();
  }

  @Test
  void rejects_null_status() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SystemStatus(
                    null, new SystemStatus.Datastore(true), CHECKED_AT, APPLICATION, RUNTIME));
  }

  @Test
  void rejects_null_datastore() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SystemStatus(
                    SystemStatus.ServiceState.UP, null, CHECKED_AT, APPLICATION, RUNTIME));
  }

  @Test
  void rejects_null_checked_at() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SystemStatus(
                    SystemStatus.ServiceState.UP,
                    new SystemStatus.Datastore(true),
                    null,
                    APPLICATION,
                    RUNTIME));
  }

  @Test
  void rejects_null_application() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SystemStatus(
                    SystemStatus.ServiceState.UP,
                    new SystemStatus.Datastore(true),
                    CHECKED_AT,
                    null,
                    RUNTIME));
  }

  @Test
  void rejects_null_runtime() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SystemStatus(
                    SystemStatus.ServiceState.UP,
                    new SystemStatus.Datastore(true),
                    CHECKED_AT,
                    APPLICATION,
                    null));
  }

  @Test
  void rejects_null_application_version() {
    assertThatNullPointerException()
        .isThrownBy(() -> new SystemStatus.ApplicationInfo(null, "production"));
  }

  @Test
  void rejects_null_application_environment() {
    assertThatNullPointerException()
        .isThrownBy(() -> new SystemStatus.ApplicationInfo("0.1.0-SNAPSHOT", null));
  }

  @Test
  void rejects_negative_uptime() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SystemStatus.RuntimeInfo(
                    "21.0.3", "Eclipse Adoptium", -1L, 0L, 0L, "Linux", "amd64"))
        .withMessageContaining("uptimeMillis");
  }

  @Test
  void rejects_negative_memory_used() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SystemStatus.RuntimeInfo(
                    "21.0.3", "Eclipse Adoptium", 0L, -1L, 0L, "Linux", "amd64"))
        .withMessageContaining("memoryUsedBytes");
  }

  @Test
  void rejects_negative_memory_max() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SystemStatus.RuntimeInfo(
                    "21.0.3", "Eclipse Adoptium", 0L, 0L, -1L, "Linux", "amd64"))
        .withMessageContaining("memoryMaxBytes");
  }

  @Test
  void carries_only_leaf_types_that_cannot_represent_connection_secrets() {
    assertThat(leafTypesOf(SystemStatus.class))
        .isNotEmpty()
        .allSatisfy(
            type ->
                assertThat(type)
                    .isIn(
                        SystemStatus.ServiceState.class, boolean.class, Instant.class,
                        String.class, long.class));
  }

  private static Stream<Class<?>> leafTypesOf(Class<?> record) {
    return Arrays.stream(record.getRecordComponents())
        .map(RecordComponent::getType)
        .flatMap(type -> type.isRecord() ? leafTypesOf(type) : Stream.of(type));
  }
}
