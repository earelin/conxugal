package gal.conxugal.infrastructure.auth;

import gal.conxugal.domain.auth.User;
import gal.conxugal.domain.auth.UserRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcUserRepository extends UserRepository, GenericRepository<User, UUID> {
}
