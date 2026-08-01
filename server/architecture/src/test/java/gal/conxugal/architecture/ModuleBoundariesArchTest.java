package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
   * {@code domain} exposes Micronaut Data as an api dependency, so persistence types now reach
   * {@code application}'s compile classpath; before that they were kept out by absence alone.
   * Driving-side code still has no business touching them — it goes through the ports.
   */
  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_PERSISTENCE =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("io.micronaut.data..", "java.sql..", "javax.sql..");

  @ArchTest
  static final ArchRule INFRASTRUCTURE_DOES_NOT_DEPEND_ON_APPLICATION =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("gal.conxugal.application..");
}
