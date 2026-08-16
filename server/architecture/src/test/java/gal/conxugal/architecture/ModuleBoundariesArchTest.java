package gal.conxugal.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

@AnalyzeClasses(packages = "gal.conxugal")
class ModuleBoundariesArchTest {

  @ArchTest
  static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OTHER_MODULES =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("gal.conxugal.application..", "gal.conxugal.infrastructure..");

  @ArchTest
  static final ArchRule COMMONS_DOES_NOT_DEPEND_ON_OTHER_MODULES =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.commons..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "gal.conxugal.domain..",
              "gal.conxugal.application..",
              "gal.conxugal.infrastructure..");

  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("gal.conxugal.infrastructure..");

  /**
   * {@code domain} exposes Micronaut Data as an api dependency, so persistence types reach
   * {@code application}'s compile classpath; before that they were kept out by absence alone.
   * Driving-side code still has no business touching them — it goes through the ports.
   *
   * <p><b>Three types are excepted, and exactly those three classes.</b> The paged-collection
   * contract puts the conversion between the framework's page and the wire envelope in the
   * controller, on the grounds that a third paging type of our own, existing only to carry two
   * integers across one module boundary, is worse than the coupling; the ordering crosses the same
   * boundary as a direction, the domain having declined a direction enum of its own for the same
   * reason. Everything else the persistence library offers — every annotation, every repository
   * type, and the whole of its runtime — stays out.
   *
   * <p><b>The exception is by identity, not by enclosing class</b>, and that distinction is the
   * whole of what the rule still protects. {@code belongToAnyOf} matches nested types too, so
   * excepting {@code Sort} would have excepted {@code Sort.Order} with it — and
   * {@code Sort.of(Sort.Order.asc(text))} in a driving adapter is precisely the ordering-from-raw-
   * input this boundary exists to make impossible. Naming {@code Sort.Order.Direction} alone keeps
   * the sort itself unbuildable here, so a later "dynamic" ordering has to move this line before it
   * can compile.
   */
  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_PERSISTENCE =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.application..")
          .should()
          .dependOnClassesThat(
              resideInAnyPackage("io.micronaut.data..", "java.sql..", "javax.sql..")
                  .and(not(equivalentTo(Page.class)
                      .or(equivalentTo(Pageable.class))
                      .or(equivalentTo(Sort.Order.Direction.class)))));

  @ArchTest
  static final ArchRule INFRASTRUCTURE_DOES_NOT_DEPEND_ON_APPLICATION =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("gal.conxugal.application..");
}
