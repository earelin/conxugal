package gal.conxugal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import io.micronaut.http.annotation.HttpMethodMapping;
import java.util.stream.Stream;

/**
 * The routes a Micronaut {@code @Controller} serves. Asking for the {@code @HttpMethodMapping}
 * meta-annotation rather than listing {@code @Get}, {@code @Post} and the rest, and over inherited
 * methods rather than declared ones, is what keeps a composed mapping or a route pulled from an
 * abstract base inside the rules below — a route these miss is a route the authorization rule
 * never looks at, and its absence would fail nothing.
 *
 * <p>Micronaut marks {@code @Error} with that same meta-annotation, so it has to come back out
 * again: an error handler runs after the security filter has already decided, has no path of its
 * own, and is not a route to secure. Excluding it is what leaves {@code SpaHistoryFallback} — a
 * {@code @Controller} whose only handler is a global {@code @Error} — outside both rules.
 */
final class HttpRoutes {

  private HttpRoutes() {
  }

  static Stream<JavaMethod> of(JavaClass javaClass) {
    return javaClass.getAllMethods().stream()
        .filter(method -> method.isMetaAnnotatedWith(HttpMethodMapping.class))
        .filter(method -> !method.isAnnotatedWith(io.micronaut.http.annotation.Error.class));
  }
}
