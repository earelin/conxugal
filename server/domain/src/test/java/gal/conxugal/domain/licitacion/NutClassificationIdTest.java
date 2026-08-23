package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NutClassificationIdTest {

  @Test
  void rejects_null_value() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NutClassificationId(null));
  }

  @Test
  void prints_as_the_bare_uuid_so_messages_read_unchanged() {
    UUID value = UUID.randomUUID();

    assertThat(new NutClassificationId(value)).hasToString(value.toString());
  }
}
