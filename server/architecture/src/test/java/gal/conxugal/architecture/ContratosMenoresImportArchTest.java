package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import gal.conxugal.domain.contrato.ImportOrganoContratosMenores;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;

/**
 * The resumption point has exactly one writer, which no type or constraint can give it.
 *
 * <p>Main sources only — the rule describes how the production code may be written, and a test
 * naming the write is exercising it rather than working around it.
 */
@AnalyzeClasses(packages = "gal.conxugal", importOptions = ImportOption.DoNotIncludeTests.class)
class ContratosMenoresImportArchTest {

  /**
   * {@code cursor_date} is written only by the initial walk. It is a conservative hint rather than
   * a ledger — held at a window's end until that window is paged out, so an interrupted import
   * re-reads an overlap instead of skipping one — and that rule only holds while one walk decides
   * it. A second writer moving the cursor on its own schedule would leave a resumption starting
   * inside a window it had not finished, and the contracts it skipped would be missing with nothing
   * to show for it.
   *
   * <p>This is why the shared window read takes no import-state port: an incremental refresh keeps
   * no resumption state, and handing it a per-batch hook instead is what keeps the cursor here.
   *
   * <p>Written as an access rather than a call, and against any implementation of the port rather
   * than its declaration alone, because "one writer" has two ways of quietly becoming two: a method
   * <em>reference</em> is not a call and would evade a call-only rule, and a caller injecting the
   * JDBC adapter concretely would reach the adapter's own re-declaration rather than the port's.
   *
   * <p>Micronaut writes the bean definition and the introduction proxy into the same output this
   * module reads, and the proxy is where the abstract write is implemented. Every one of those
   * classes is named with a leading {@code $}, which no hand-written class here is — matched on the
   * <em>full</em> name rather than the simple one, because the generated dispatcher is a nested
   * class whose simple name is the innermost segment alone and carries no {@code $} at all.
   */
  @ArchTest
  static final ArchRule ONLY_THE_INITIAL_WALK_MOVES_THE_CURSOR =
      noClasses()
          .that()
          .doNotBelongToAnyOf(ImportOrganoContratosMenores.class)
          .and()
          .haveNameNotMatching(".*\\.\\$.*")
          .should()
          .accessTargetWhere(theCursorWrite())
          .because(
              "the resumption point is the initial walk's alone; the shared window read reaches it"
                  + " through a per-batch hook and cannot write it");

  /** The port's own declaration and every implementation's re-declaration of it alike. */
  private static DescribedPredicate<JavaAccess<?>> theCursorWrite() {
    return DescribedPredicate.describe(
        "a write of the contratos menores import cursor",
        access -> {
          AccessTarget target = access.getTarget();
          return "updateCursorDate".equals(target.getName())
              && target.getOwner().isAssignableTo(ContratosMenoresImportStateRepository.class);
        });
  }
}
