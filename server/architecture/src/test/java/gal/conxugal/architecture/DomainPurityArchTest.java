package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "gal.conxugal")
class DomainPurityArchTest {

  @ArchTest
  static final ArchRule DOMAIN_IS_FREE_OF_TRANSPORT_AND_PERSISTENCE_CODE =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "io.micronaut.http..",
              "io.micronaut.security..",
              "io.micronaut.views..",
              "io.micronaut.jdbc..",
              "io.micronaut.data.jdbc..",
              "io.micronaut.data.exceptions..",
              "java.sql..",
              "javax.sql..",
              "org.postgresql..",
              "org.flywaydb..");
}
