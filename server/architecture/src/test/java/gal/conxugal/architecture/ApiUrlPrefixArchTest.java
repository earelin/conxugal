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
import io.micronaut.http.annotation.Produces;
import java.util.Arrays;

@AnalyzeClasses(packages = "gal.conxugal")
class ApiUrlPrefixArchTest {

  private static final DescribedPredicate<JavaClass> SERVES_A_NON_HTML_RESPONSE =
      new DescribedPredicate<>("serve a response that is not text/html") {
        @Override
        public boolean test(JavaClass javaClass) {
          return HttpRoutes.of(javaClass).anyMatch(route -> !producesHtml(route));
        }
      };

  @ArchTest
  static final ArchRule REST_CONTROLLERS_ARE_MOUNTED_UNDER_API_PREFIX =
      classes()
          .that()
          .areAnnotatedWith(Controller.class)
          .and(SERVES_A_NON_HTML_RESPONSE)
          .should(haveControllerPathStartingWithApi());

  private static boolean producesHtml(JavaMethod method) {
    if (method.isAnnotatedWith(Produces.class)) {
      return producesHtml(method.getAnnotationOfType(Produces.class));
    }
    JavaClass owner = method.getOwner();
    return owner.isAnnotatedWith(Produces.class)
        && producesHtml(owner.getAnnotationOfType(Produces.class));
  }

  private static boolean producesHtml(Produces produces) {
    return Arrays.asList(produces.value()).contains(MediaType.TEXT_HTML);
  }

  private static ArchCondition<JavaClass> haveControllerPathStartingWithApi() {
    return new ArchCondition<>("have a @Controller path starting with '/api'") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String basePath = javaClass.getAnnotationOfType(Controller.class).value();
        boolean satisfied = "/api".equals(basePath) || basePath.startsWith("/api/");
        String message =
            String.format(
                "%s is @Controller(\"%s\"), which serves a non-HTML response outside /api",
                javaClass.getFullName(), basePath);
        events.add(new SimpleConditionEvent(javaClass, satisfied, message));
      }
    };
  }
}
