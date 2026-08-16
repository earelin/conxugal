package gal.conxugal.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
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
   * <p><b>Three types are excepted, and only three.</b>
   * {@code docs/architecture/0022-paged-collection-contract-from-micronaut-data.md} puts the
   * conversion between the framework's page and the contract's envelope in the controller, on the
   * grounds that a third paging type owned by us, existing only to carry two integers across one
   * module boundary, is worse than the coupling. The ordering travels the same way, as
   * {@code Sort.Order.Direction}, because the domain declined a direction enum of its own for the
   * same reason. That is a widening the record states and accepts; everything else Micronaut Data
   * offers — every annotation, every repository type, and the whole of its runtime — stays out,
   * which is what keeps the exception the size the ADR made it.
   */
  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_PERSISTENCE =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.application..")
          .should()
          .dependOnClassesThat(
              resideInAnyPackage("io.micronaut.data..", "java.sql..", "javax.sql..")
                  .and(not(belongToAnyOf(Page.class, Pageable.class, Sort.class))));

  @ArchTest
  static final ArchRule INFRASTRUCTURE_DOES_NOT_DEPEND_ON_APPLICATION =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("gal.conxugal.application..");
}
