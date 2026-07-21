package gal.conxugal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-15T09:30:00Z");

  @Test
  void exposes_id_email_password_hash_and_role() {
    UUID id = UUID.randomUUID();
    User user = new User(id, "ana@example.com", "stored-hash", Role.ADMIN, true, CREATED_AT);

    assertThat(user.id()).isEqualTo(id);
    assertThat(user.email()).isEqualTo("ana@example.com");
    assertThat(user.passwordHash()).isEqualTo("stored-hash");
    assertThat(user.role()).isEqualTo(Role.ADMIN);
  }

  @Test
  void exposes_enabled_and_created_at() {
    User user =
        new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.USER, false, CREATED_AT);

    assertThat(user.enabled()).isFalse();
    assertThat(user.createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void has_no_last_login_at_when_constructed_without_one() {
    User user =
        new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.USER, true, CREATED_AT);

    assertThat(user.lastLoginAt()).isNull();
  }

  @Test
  void exposes_the_given_last_login_at() {
    Instant lastLoginAt = Instant.parse("2026-07-11T10:15:30Z");
    User user =
        new User(
            UUID.randomUUID(), "ana@example.com", "stored-hash", Role.USER, true, CREATED_AT,
            lastLoginAt);

    assertThat(user.lastLoginAt()).isEqualTo(lastLoginAt);
  }

  @Test
  void allows_null_id_before_being_persisted() {
    User user = new User(null, "ana@example.com", "stored-hash", Role.USER, true, CREATED_AT);

    assertThat(user.id()).isNull();
  }

  @Test
  void rejects_null_email() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new User(UUID.randomUUID(), null, "stored-hash", Role.USER, true, CREATED_AT));
  }

  @Test
  void rejects_null_password_hash() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new User(UUID.randomUUID(), "ana@example.com", null, Role.USER, true,
                CREATED_AT));
  }

  @Test
  void rejects_null_role() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new User(UUID.randomUUID(), "ana@example.com", "stored-hash", null, true,
                CREATED_AT));
  }

  @Test
  void rejects_null_created_at() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.USER, true,
                null));
  }

  @Test
  void toString_redacts_password_hash() {
    User user =
        new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.ADMIN, true, CREATED_AT);

    assertThat(user.toString())
        .contains("ana@example.com", "ADMIN")
        .doesNotContain("stored-hash");
  }
}
