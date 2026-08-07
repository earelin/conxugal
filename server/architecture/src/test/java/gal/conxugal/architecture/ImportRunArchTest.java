package gal.conxugal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.infrastructure.jdbc.importrun.JdbcImportRunRepository;

/**
 * The two guarantees the import guard rests on that no type or constraint can give it.
 *
 * <p>Main sources only — the rules describe how the production code may be written, and a test
 * naming either is exercising them rather than working around them.
 */
@AnalyzeClasses(packages = "gal.conxugal", importOptions = ImportOption.DoNotIncludeTests.class)
class ImportRunArchTest {

  /**
   * The abandoned state is derived in one place and stored nowhere. Two readers deriving it
   * separately could disagree about whether an import may start, which is the one question the
   * guard exists to settle; and a code path that <em>wrote</em> it would turn a run that is merely
   * quiet into one recorded as dead, from which no resumption can tell it apart.
   *
   * <p>Reads rather than accesses, because the enum's own initialiser writes the constant.
   */
  @ArchTest
  static final ArchRule ONLY_THE_STATE_ITSELF_NAMES_ABANDONED =
      noClasses()
          .that()
          .doNotBelongToAnyOf(ImportRunState.class)
          .should()
          .getField(ImportRunState.class, "ABANDONED");

  /**
   * A run row is inserted in exactly one place, the claim. This is the guarantee a partial unique
   * index would have given for free: the lock lives inside the claim, so a second insertion path
   * would step past the guard with nothing to show for it — no error, just two imports at once.
   */
  @ArchTest
  static final ArchRule ONLY_THE_CLAIM_INSERTS_ONE =
      methods()
          .that()
          .areDeclaredIn(JdbcImportRunRepository.class)
          .and()
          .haveName("insert")
          .should()
          .onlyBeCalled()
          .byCodeUnitsThat(theClaimOrTheFrameworksOwnPlumbing());

  private static DescribedPredicate<JavaCodeUnit> theClaimOrTheFrameworksOwnPlumbing() {
    return new DescribedPredicate<>("the claim, or the framework's own generated plumbing") {
      @Override
      public boolean test(JavaCodeUnit codeUnit) {
        JavaClass owner = codeUnit.getOwner();
        if (isGenerated(owner.getName())) {
          return true;
        }
        return owner.isEquivalentTo(JdbcImportRunRepository.class)
            && "claim".equals(codeUnit.getName());
      }
    };
  }

  /**
   * Micronaut writes the bean definition and the introduction proxy into the same output the
   * architecture module reads, and the proxy is where the abstract insert is implemented. Every
   * one of those classes is named with a leading {@code $}, which no hand-written class here is.
   */
  private static boolean isGenerated(String className) {
    return className.substring(className.lastIndexOf('.') + 1).startsWith("$");
  }
}
