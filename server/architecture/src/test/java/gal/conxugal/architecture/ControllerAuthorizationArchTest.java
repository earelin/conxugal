package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.micronaut.http.annotation.Controller;
import io.micronaut.security.annotation.Secured;

/**
 * Authorization is declared twice — once in {@code application.yml}'s {@code intercept-url-map}
 * and once per controller — and nothing makes the two agree. A route carrying no annotation does
 * not fall open: the annotation rule is consulted first, so the route drops through to the URL
 * map's catch-all and quietly becomes merely authenticated. That silent downgrade is what this
 * catches — an administration route mounted outside the {@code /api/admin} prefix would be served
 * to any signed-in user rather than refused. Which rule applies, and whether it matches the URL
 * map, stays the integration tests' to show.
 */
@AnalyzeClasses(packages = "gal.conxugal")
class ControllerAuthorizationArchTest {

  private static final DescribedPredicate<JavaClass> DECLARES_A_ROUTE =
      new DescribedPredicate<>("declare at least one HTTP route") {
        @Override
        public boolean test(JavaClass javaClass) {
          return HttpRoutes.of(javaClass).findAny().isPresent();
        }
      };

  @ArchTest
  static final ArchRule EVERY_ROUTE_DECLARES_ITS_AUTHORIZATION_RULE =
      classes()
          .that()
          .areAnnotatedWith(Controller.class)
          .and(DECLARES_A_ROUTE)
          .should(carrySecuredOnTheClassOrOnEveryRoute());

  /**
   * Both placements are in use and both are legitimate — the REST controllers annotate the class,
   * the two HTML pages annotate the method — so the condition is per route rather than per class:
   * a second handler added to a class-less-annotated controller has to bring its own rule.
   */
  private static ArchCondition<JavaClass> carrySecuredOnTheClassOrOnEveryRoute() {
    return new ArchCondition<>("carry @Secured on the class or on every route") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean securedController = javaClass.isAnnotatedWith(Secured.class);
        HttpRoutes.of(javaClass)
            .forEach(
                route -> {
                  boolean satisfied = securedController || route.isAnnotatedWith(Secured.class);
                  String message =
                      String.format(
                          "%s carries no @Secured, on the route or on its controller",
                          route.getFullName());
                  events.add(new SimpleConditionEvent(route, satisfied, message));
                });
      }
    };
  }
}
