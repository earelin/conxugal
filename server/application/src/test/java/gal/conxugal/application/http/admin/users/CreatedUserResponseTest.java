package gal.conxugal.application.http.admin.users;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.user.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatedUserResponseTest {

  @Test
  void toString_redacts_the_initial_password() {
    CreatedUserResponse response = new CreatedUserResponse(
        UUID.randomUUID(), "ana@example.com", Role.ADMIN, true,
        Instant.parse("2026-07-18T09:30:00Z"), "Tg7#kLp2Qw9$mZxR");

    assertThat(response.toString()).doesNotContain("Tg7#kLp2Qw9$mZxR");
  }
}
