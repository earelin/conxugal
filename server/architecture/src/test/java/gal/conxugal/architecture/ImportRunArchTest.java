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
import gal.conxugal.domain.importrun.ImportRun;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.infrastructure.jdbc.importrun.JdbcImportRunRepository;

/**
 * The three guarantees the import guard rests on that no type or constraint can give it.
 *
 * <p>Main sources only — the rules describe how the production code may be written, and a test
 * naming either is exercising them rather than working around them.
 */
@AnalyzeClasses(packages = "gal.conxugal", importOptions = ImportOption.DoNotIncludeTests.class)
class ImportRunArchTest {

  // By name, so this module needs no compile dependency on Micronaut Data.
  private static final String INSERT = "io.micronaut.data.annotation.Insert";

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
   * The value cannot be reached around the rule above either. The column is bare {@code text} with
   * no check constraint, deliberately, so a name resolved at runtime would be stored as readily as
   * the constant itself.
   */
  @ArchTest
  static final ArchRule ONLY_THE_STATE_ITSELF_RESOLVES_ONE_BY_NAME =
      noClasses()
          .that()
          .doNotBelongToAnyOf(ImportRunState.class)
          .should()
          .callMethod(ImportRunState.class, "valueOf", String.class);

  /**
   * A run row is inserted in exactly one place, the claim. This is the guarantee a partial unique
   * index would have given for free: the lock lives inside the claim, so a second insertion path
   * would step past the guard with nothing to show for it — no error, just two imports at once.
   *
   * <p>Three rules, because "one place" has three ways of quietly becoming two: a second caller of
   * the insert, a second insert method, or a run built somewhere else and handed to either.
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

  @ArchTest
  static final ArchRule THE_CLAIMS_ADAPTER_DECLARES_THE_ONLY_INSERT =
      methods()
          .that()
          .areAnnotatedWith(INSERT)
          .and()
          .haveRawParameterTypes(ImportRun.class)
          .should()
          .beDeclaredIn(JdbcImportRunRepository.class)
          .andShould()
          .haveName("insert");

  @ArchTest
  static final ArchRule ONLY_THE_CLAIM_MAKES_ONE =
      methods()
          .that()
          .areDeclaredIn(ImportRun.class)
          .and()
          .haveName("claimedAt")
          .should()
          .onlyBeCalled()
          .byCodeUnitsThat(theClaimOrTheFrameworksOwnPlumbing());

  /**
   * A coverage row is written in exactly one place too, and no rule above sees it: it is raw SQL in
   * a private method rather than a Micronaut Data insert. Re-keying the coverage to admit a second
   * family per Órgano is precisely the change that makes a second insertion path tempting — adding
   * a family to a run already claimed — and such a path would write its row outside the transaction
   * that holds the guard.
   */
  @ArchTest
  static final ArchRule ONLY_THE_CLAIM_ENUMERATES_COVERAGE =
      methods()
          .that()
          .areDeclaredIn(JdbcImportRunRepository.class)
          .and()
          .haveName("enumerateCoverage")
          .should()
          .onlyBeCalled()
          .byCodeUnitsThat(theClaimOrTheFrameworksOwnPlumbing());

  /**
   * And the rule above cannot be reached around by a second method beside the enumeration, because
   * any path that starts a coverage row has to name the state one starts at.
   */
  @ArchTest
  static final ArchRule ONLY_THE_CLAIMS_ADAPTER_STARTS_A_COVERAGE_ROW =
      noClasses()
          .that()
          .doNotBelongToAnyOf(ImportRunOrganoState.class, JdbcImportRunRepository.class)
          .should()
          .getField(ImportRunOrganoState.class, "PENDING");

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
