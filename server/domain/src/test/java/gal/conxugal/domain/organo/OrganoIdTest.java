package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganoIdTest {

  @Test
  void rejects_null_value() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OrganoId(null));
  }

  @Test
  void prints_as_the_bare_uuid_so_messages_read_unchanged() {
    UUID value = UUID.randomUUID();

    assertThat(new OrganoId(value)).hasToString(value.toString());
  }
}
