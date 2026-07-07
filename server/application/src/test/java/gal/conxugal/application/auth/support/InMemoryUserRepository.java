package gal.conxugal.application.auth.support;

import gal.conxugal.domain.auth.User;
import gal.conxugal.domain.auth.UserRepository;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Replaces(UserRepository.class)
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();

    public void save(User user) {
        usersByEmail.put(user.email(), user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(email));
    }
}
