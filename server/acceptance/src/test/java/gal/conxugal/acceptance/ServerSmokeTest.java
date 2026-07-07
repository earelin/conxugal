package gal.conxugal.acceptance;

import static io.restassured.RestAssured.when;

import gal.conxugal.acceptance.support.ApplicationUnderTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves the acceptance harness itself: it can reach an already-running application
 * instance over HTTP. Real user-scenario tests replace/join this one as features
 * land.
 */
class ServerSmokeTest {

    @BeforeAll
    static void connect() {
        RestAssured.baseURI = ApplicationUnderTest.BASE_URI;
    }

    @Test
    void unmapped_route_returns_not_found() {
        when()
                .get("/does-not-exist")
        .then()
                .statusCode(404);
    }
}
