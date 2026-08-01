package gal.conxugal.infrastructure.http;

import gal.conxugal.infrastructure.version.ApplicationVersion;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.core.annotation.NonNull;
import java.util.Map;

/**
 * How conxugal identifies itself to an external source. An operator who can reach us can ask us to
 * slow down; one who cannot can only block us.
 *
 * <p>Published as a property because {@link ResilientClient} declares it as a {@code @Header}, and
 * an annotation value can only be a constant or a placeholder — the running version is neither
 * until the process starts. Every client carrying the advice therefore sends it, without any
 * declaration of its own.
 */
@ContextConfigurer
public class UserAgent implements ApplicationContextConfigurer {

  public static final String PROPERTY = "conxugal.user-agent";

  static final String VALUE =
      "conxugal/%s (+https://github.com/earelin/conxugal)".formatted(ApplicationVersion.read());

  @Override
  public void configure(@NonNull ApplicationContextBuilder builder) {
    builder.properties(Map.of(PROPERTY, VALUE));
  }
}
