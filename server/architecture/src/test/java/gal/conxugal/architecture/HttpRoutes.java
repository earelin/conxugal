package gal.conxugal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.Options;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Trace;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The routes a Micronaut {@code @Controller} declares — its methods carrying an HTTP method
 * mapping. A {@code @Controller} holding only {@code @Error} handlers declares none, which is why
 * the rules here ask a class for its routes rather than assuming every controller serves any.
 */
final class HttpRoutes {

  private static final Set<Class<? extends Annotation>> HTTP_METHOD_MAPPINGS =
      Set.of(
          Get.class, Post.class, Put.class, Delete.class, Patch.class, Head.class, Options.class,
          Trace.class);

  private HttpRoutes() {
  }

  static Stream<JavaMethod> of(JavaClass javaClass) {
    return javaClass.getMethods().stream().filter(HttpRoutes::isRoute);
  }

  private static boolean isRoute(JavaMethod method) {
    return HTTP_METHOD_MAPPINGS.stream().anyMatch(method::isAnnotatedWith);
  }
}
