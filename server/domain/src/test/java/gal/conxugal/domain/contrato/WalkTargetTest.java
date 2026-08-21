package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.OrganoId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The three facts a walk carries unchanged from end to end, and why none of them may be absent.
 *
 * <p>Refusing at construction is the whole point: an Órgano that was never stored has no id, and a
 * walk built on one would ask the source for a page before failing on it — with the request already
 * issued and the failure surfacing inside the batch store rather than where the mistake was made.
 */
class WalkTargetTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final String SOURCE_KEY = "242";

  @Test
  void rejects_the_walk_that_names_no_run() {
    assertThatNullPointerException()
        .isThrownBy(() -> new WalkTarget(null, ORGANO_ID, SOURCE_KEY))
        .withMessageContaining("runId");
  }

  @Test
  void rejects_the_walk_that_names_no_organo() {
    assertThatNullPointerException()
        .isThrownBy(() -> new WalkTarget(RUN_ID, null, SOURCE_KEY))
        .withMessageContaining("organoId");
  }

  @Test
  void rejects_the_walk_that_names_no_source_key() {
    assertThatNullPointerException()
        .isThrownBy(() -> new WalkTarget(RUN_ID, ORGANO_ID, null))
        .withMessageContaining("sourceKey");
  }
}
