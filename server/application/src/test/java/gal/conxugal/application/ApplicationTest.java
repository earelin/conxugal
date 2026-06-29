package gal.conxugal.application;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test: the Micronaut context starts and the embedded server runs with the
 * three hexagonal modules assembled (application + domain + infrastructure).
 */
@MicronautTest
class ApplicationTest {

    @Test
    void applicationStarts(EmbeddedApplication<?> application) {
        assertTrue(application.isRunning());
    }
}
