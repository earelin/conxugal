package gal.conxugal.application.rest.admin.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.status.SystemStatus;
import gal.conxugal.domain.status.SystemStatusProbe;
import gal.conxugal.domain.user.User;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@MicronautTest
class SystemStatusControllerIntegrationTest extends AuthenticationTestSupport {

  private static final Instant CHECKED_AT = Instant.parse("2026-07-18T09:30:00Z");
  private static final SystemStatus.ApplicationInfo APPLICATION =
      new SystemStatus.ApplicationInfo("0.1.0-SNAPSHOT", "production");
  private static final SystemStatus.RuntimeInfo RUNTIME =
      new SystemStatus.RuntimeInfo(
          "21.0.3", "Eclipse Adoptium", 266320000L, 536870912L, 1073741824L, "Linux", "amd64");

  @Inject
  SystemStatusProbe systemStatusProbe;

  @MockBean(SystemStatusProbe.class)
  SystemStatusProbe systemStatusProbeMock() {
    return mock(SystemStatusProbe.class);
  }

  @Test
  void user_role_is_forbidden(RequestSpecification spec) {
    seedUser(TestUserFactory.normalUser());
    String sessionCookie = loginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/admin/system-status")
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .get("/api/admin/system-status")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void admin_sees_up_status_when_the_datastore_is_reachable(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    when(systemStatusProbe.currentStatus())
        .thenReturn(SystemStatus.assess(true, CHECKED_AT, APPLICATION, RUNTIME));
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/api/admin/system-status");

    response.then().statusCode(HttpStatus.OK.getCode());
    assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
    assertThat(response.jsonPath().getBoolean("datastore.reachable")).isTrue();
    assertThat(response.jsonPath().getString("application.version")).isEqualTo("0.1.0-SNAPSHOT");
    assertThat(response.jsonPath().getString("application.environment")).isEqualTo("production");
    assertThat(response.jsonPath().getString("runtime.javaVersion")).isEqualTo("21.0.3");
    assertThat(response.jsonPath().getString("runtime.osName")).isEqualTo("Linux");
    assertThat(response.jsonPath().getLong("runtime.uptimeMillis")).isEqualTo(266320000L);
  }

  @Test
  void admin_sees_degraded_status_when_the_datastore_is_unreachable(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    when(systemStatusProbe.currentStatus())
        .thenReturn(SystemStatus.assess(false, CHECKED_AT, APPLICATION, RUNTIME));
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/api/admin/system-status");

    response.then().statusCode(HttpStatus.OK.getCode());
    assertThat(response.jsonPath().getString("status")).isEqualTo("DEGRADED");
    assertThat(response.jsonPath().getBoolean("datastore.reachable")).isFalse();
  }

  private String loginAs(RequestSpecification spec, User user) {
    Response response =
        given(spec)
            .body(
                "{\"username\":\"" + user.email() + "\",\"password\":\"" + user.passwordHash()
                    + "\"}")
        .when()
            .post("/login");
    return sessionCookieOf(response);
  }
}
