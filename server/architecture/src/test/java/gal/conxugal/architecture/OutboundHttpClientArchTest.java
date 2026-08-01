package gal.conxugal.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import gal.conxugal.infrastructure.http.ResilientClient;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;

/**
 * ADR-0014: every outbound call reaches its source through a declarative {@code @Client} interface
 * carrying the resilience advice. Between them the two rules below leave no way to reach a source
 * unpoliced — one closes off building a client, the other declaring one without the advice.
 *
 * <p>Main sources only — a test may still drive a client directly.
 */
@AnalyzeClasses(packages = "gal.conxugal", importOptions = ImportOption.DoNotIncludeTests.class)
class OutboundHttpClientArchTest {

  @ArchTest
  static final ArchRule INFRASTRUCTURE_DOES_NOT_NAME_AN_HTTP_CLIENT_TYPE =
      noClasses()
          .that()
          .resideInAPackage("gal.conxugal.infrastructure..")
          .should()
          .dependOnClassesThat(
              assignableTo(HttpClient.class)
                  .or(assignableTo(BlockingHttpClient.class))
                  .as("are a Micronaut HTTP client"));

  /**
   * With no client left to hand-build, the one remaining way to reach a source unpoliced is to
   * declare an interface and forget the advice — which yields a working, unpaced, unidentified
   * client that the rule above cannot see.
   */
  @ArchTest
  static final ArchRule DECLARATIVE_CLIENTS_CARRY_THE_RESILIENCE_ADVICE =
      classes()
          .that()
          .resideInAPackage("gal.conxugal.infrastructure..")
          .and()
          .areAnnotatedWith(Client.class)
          .should()
          .beAnnotatedWith(ResilientClient.class);
}
