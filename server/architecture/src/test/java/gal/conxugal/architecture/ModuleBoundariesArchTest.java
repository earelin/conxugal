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

  @ArchTest
  static final ArchRule INFRASTRUCTURE_DOES_NOT_DEPEND_ON_APPLICATION =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("gal.conxugal.application..");
}
