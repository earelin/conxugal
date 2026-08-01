package gal.conxugal.application.rest.user;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserId;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@MicronautTest
class CurrentUserControllerIntegrationTest extends AuthenticationTestSupport {

  @Test
  void authenticated_user_sees_their_own_account(RequestSpecification spec) {
    User user = TestUserFactory.normalUser();
    seedUser(user);
    String sessionCookie = loginAs(spec, user);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/api/me");

    response.then().statusCode(HttpStatus.OK.getCode());
    assertThat(new UserId(UUID.fromString(response.jsonPath().getString("id"))))
        .isEqualTo(user.id());
    assertThat(response.jsonPath().getString("email")).isEqualTo(user.email());
    assertThat(response.jsonPath().getString("role")).isEqualTo("USER");
    assertThat(response.jsonPath().getString("createdAt")).isNotNull();
    assertThat(response.jsonPath().getString("lastLoginAt")).isNull();
    assertThat(response.jsonPath().getObject("enabled", Boolean.class)).isNull();
    assertThat(response.jsonPath().getString("passwordHash")).isNull();
  }

  @Test
  void authenticated_admin_sees_their_own_account_with_the_admin_role(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/api/me");

    response.then().statusCode(HttpStatus.OK.getCode());
    assertThat(response.jsonPath().getString("role")).isEqualTo("ADMIN");
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .get("/api/me")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }
}
