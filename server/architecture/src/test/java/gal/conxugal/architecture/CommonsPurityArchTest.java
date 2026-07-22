package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "gal.conxugal")
class CommonsPurityArchTest {

  @ArchTest
  static final ArchRule COMMONS_IS_FREE_OF_FRAMEWORK_CODE =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.commons..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "io.micronaut..",
              "jakarta.inject..",
              "javax.inject..",
              "java.sql..",
              "javax.sql..",
              "org.postgresql..",
              "org.flywaydb..");
}
