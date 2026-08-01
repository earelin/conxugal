package gal.conxugal.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;

/**
 * ADR-0014: every outbound call reaches its source through a declarative {@code @Client} interface
 * carrying the resilience advice. An adapter that names a client type is one that could build its
 * own, unpaced and unretried, so naming either type is a build failure rather than a review catch.
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
}
