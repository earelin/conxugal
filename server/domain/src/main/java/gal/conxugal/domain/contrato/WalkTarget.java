package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.OrganoId;

/**
 * What one walk is about: the Órgano it is loading, the key the source knows that Órgano by, and
 * the run it reports its progress against. None of the three moves while the walk runs, so they
 * travel together rather than as three more parameters on every step of it.
 */
public record WalkTarget(ImportRunId runId, OrganoId organoId, String sourceKey) {}
