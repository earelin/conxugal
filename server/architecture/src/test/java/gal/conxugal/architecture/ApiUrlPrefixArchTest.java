package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.Options;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Trace;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

@AnalyzeClasses(packages = "gal.conxugal")
class ApiUrlPrefixArchTest {

  private static final Set<Class<? extends Annotation>> HTTP_METHOD_MAPPINGS =
      Set.of(
          Get.class, Post.class, Put.class, Delete.class, Patch.class, Head.class, Options.class,
          Trace.class);

  private static final DescribedPredicate<JavaClass> SERVES_A_NON_HTML_RESPONSE =
      new DescribedPredicate<>("serve a response that is not text/html") {
        @Override
        public boolean test(JavaClass javaClass) {
          return javaClass.getMethods().stream()
              .filter(ApiUrlPrefixArchTest::isHandlerMethod)
              .anyMatch(method -> !producesHtml(method));
        }
      };

  // allowEmptyShould: no REST (non-HTML) @Controller exists yet — this rule guards
  // the ones FEAT-0004's TASK-0003/TASK-0004 are about to add.
  @ArchTest
  static final ArchRule REST_CONTROLLERS_ARE_MOUNTED_UNDER_API_PREFIX =
      classes()
          .that()
          .areAnnotatedWith(Controller.class)
          .and(SERVES_A_NON_HTML_RESPONSE)
          .should(haveControllerPathStartingWithApi())
          .allowEmptyShould(true);

  private static boolean isHandlerMethod(JavaMethod method) {
    return HTTP_METHOD_MAPPINGS.stream().anyMatch(method::isAnnotatedWith);
  }

  private static boolean producesHtml(JavaMethod method) {
    if (method.isAnnotatedWith(Produces.class)) {
      return producesHtml(method.getAnnotationOfType(Produces.class));
    }
    JavaClass owner = method.getOwner();
    if (owner.isAnnotatedWith(Produces.class)) {
      return producesHtml(owner.getAnnotationOfType(Produces.class));
    }
    return false;
  }

  private static boolean producesHtml(Produces produces) {
    return Arrays.asList(produces.value()).contains(MediaType.TEXT_HTML);
  }

  private static ArchCondition<JavaClass> haveControllerPathStartingWithApi() {
    return new ArchCondition<>("have a @Controller path starting with '/api'") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String basePath = javaClass.getAnnotationOfType(Controller.class).value();
        boolean satisfied = basePath.equals("/api") || basePath.startsWith("/api/");
        String message =
            String.format(
                "%s is @Controller(\"%s\"), which serves a non-HTML response outside /api",
                javaClass.getFullName(), basePath);
        events.add(new SimpleConditionEvent(javaClass, satisfied, message));
      }
    };
  }
}
