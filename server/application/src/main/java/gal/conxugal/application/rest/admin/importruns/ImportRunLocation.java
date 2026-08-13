package gal.conxugal.application.rest.admin.importruns;

import gal.conxugal.domain.importrun.ImportRunId;
import java.net.URI;

/**
 * Where one run is read: the route the read serves, and the link a trigger points at it with.
 *
 * <p>The two are the same fact, and a trigger that spelled the path itself would keep pointing at
 * the old one after a rename — answering a {@code Location} that leads nowhere, which is the one
 * thing a client of an asynchronous trigger has to be able to follow.
 */
public final class ImportRunLocation {

  public static final String PATH = "/api/admin/import-run";

  private ImportRunLocation() {}

  /** The run read for {@code runId}. */
  public static URI of(ImportRunId runId) {
    return URI.create("%s/%s".formatted(PATH, runId));
  }
}
