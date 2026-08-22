package gal.conxugal.infrastructure.jdbc.importrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A coverage row is inserted by exactly one statement, and this is the only check that can say so.
 *
 * <p>The guard is a lock taken inside the claim rather than a constraint on the table, so a second
 * path that writes a coverage row — a family added to a run already claimed, which is the obvious
 * next thing to want — would put that row outside the transaction holding the lock, with no error
 * to show for it. {@code ImportRunArchTest} covers the run row, and covers the enumeration's
 * <em>callers</em>; neither reaches this, because the coverage write is raw SQL and no ArchUnit
 * predicate looks inside a string.
 *
 * <p>So the statement is counted where it survives compilation: every string literal in a class
 * lands in its constant pool, whether it was written as a constant or inline at the call site, so
 * scanning the compiled class finds both. Comments and identifiers do not appear, which is what
 * makes the count mean statements rather than mentions.
 *
 * <p>Case and line wrapping are normalised away, so re-formatting the SQL does not break this. A
 * statement that reached the table by some other spelling still would — building the name up from
 * pieces, say — and that is the bound of what a check at this level can promise.
 */
class ImportRunCoverageInsertionPathTest {

  private static final String THE_COVERAGE_INSERT = "INSERT INTO IMPORT_RUN_ORGANO";

  @Test
  void only_one_statement_in_the_claims_adapter_inserts_the_coverage_rows() throws IOException {
    assertThat(occurrencesIn(compiled(JdbcImportRunRepository.class), THE_COVERAGE_INSERT))
        .describedAs(
            "%s must hold exactly one statement inserting a coverage row, and the claim must be"
                + " what runs it — a second one would write outside the guard's transaction",
            JdbcImportRunRepository.class.getSimpleName())
        .isOne();
  }

  /**
   * The class as javac left it, read off the test classpath rather than off a source path, so
   * nothing here depends on where the module sits on disk. ISO-8859-1 because the bytes are being
   * searched rather than decoded: it maps every byte to one character, so no multi-byte sequence
   * can swallow the ASCII being looked for.
   */
  private static String compiled(Class<?> type) throws IOException {
    String resource = "/%s.class".formatted(type.getName().replace('.', '/'));
    try (InputStream bytecode = type.getResourceAsStream(resource)) {
      if (bytecode == null) {
        throw new IllegalStateException("%s is not on the test classpath".formatted(resource));
      }
      return new String(bytecode.readAllBytes(), StandardCharsets.ISO_8859_1)
          .toUpperCase(Locale.ROOT)
          .replaceAll("\\s+", " ");
    }
  }

  private static int occurrencesIn(String haystack, String needle) {
    int found = 0;
    for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
      found++;
    }
    return found;
  }
}
