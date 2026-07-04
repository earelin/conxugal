package gal.conxugal.domain.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserTest {

    @Test
    void exposes_email_password_hash_and_role() {
        User user = new User("ana@example.com", "stored-hash", Role.ADMIN);

        assertThat(user.email()).isEqualTo("ana@example.com");
        assertThat(user.passwordHash()).isEqualTo("stored-hash");
        assertThat(user.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejects_null_email() {
        assertThatNullPointerException()
            .isThrownBy(() -> new User(null, "stored-hash", Role.USER));
    }

    @Test
    void rejects_null_password_hash() {
        assertThatNullPointerException()
            .isThrownBy(() -> new User("ana@example.com", null, Role.USER));
    }

    @Test
    void rejects_null_role() {
        assertThatNullPointerException()
            .isThrownBy(() -> new User("ana@example.com", "stored-hash", null));
    }

    @Test
    void toString_redacts_password_hash() {
        User user = new User("ana@example.com", "stored-hash", Role.ADMIN);

        assertThat(user.toString())
            .contains("ana@example.com", "ADMIN")
            .doesNotContain("stored-hash");
    }
}
