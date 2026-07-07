package gal.conxugal.application.auth;

import gal.conxugal.application.auth.support.InMemoryUserRepository;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
@Property(name = "micronaut.session.max-inactive-interval", value = "1s")
class IdleSessionTimeoutTest {

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    InMemoryUserRepository userRepository;

    private BlockingHttpClient client;

    @BeforeEach
    void set_up() {
        client = HttpClient.create(embeddedServer.getURL()).toBlocking();
        userRepository.save(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
    }

    @Test
    void protected_request_is_rejected_after_the_session_has_been_idle_past_the_timeout() throws InterruptedException {
        HttpResponse<?> loginResponse = client.exchange(HttpRequest
                .POST("/login", new UsernamePasswordCredentials("user@example.com", "user-password"))
                .contentType(MediaType.APPLICATION_JSON_TYPE));
        String sessionCookie = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        Thread.sleep(1500);

        assertThat(statusOf(HttpRequest.GET("/api/data").header(HttpHeaders.COOKIE, sessionCookie)))
                .isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    private int statusOf(HttpRequest<?> request) {
        try {
            return client.exchange(request).getStatus().getCode();
        } catch (HttpClientResponseException e) {
            return e.getStatus().getCode();
        }
    }
}
