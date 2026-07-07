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
class CsrfProtectionTest {

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
    void state_changing_request_with_a_session_but_no_csrf_token_is_rejected() {
        HttpResponse<?> loginResponse = client.exchange(HttpRequest
                .POST("/login", new UsernamePasswordCredentials("user@example.com", "user-password"))
                .contentType(MediaType.APPLICATION_JSON_TYPE));
        String sessionCookie = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        HttpRequest<?> request = HttpRequest.POST("/api/data", "")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .header(HttpHeaders.COOKIE, sessionCookie);

        assertThat(statusOf(request)).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    private int statusOf(HttpRequest<?> request) {
        try {
            return client.exchange(request).getStatus().getCode();
        } catch (HttpClientResponseException e) {
            return e.getStatus().getCode();
        }
    }
}
