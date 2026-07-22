package gal.conxugal.infrastructure.jdbc.user;

import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcUserRepository extends UserRepository, GenericRepository<User, UUID> {
}
