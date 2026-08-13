package gal.conxugal.application.rest.admin.organos;

/**
 * Why marking an Órgano started no import. The same two refusals the triggers report as problem
 * types, named rather than typed because here they arrive on a {@code 200}: the mark applied, and
 * only the import it asked for did not happen.
 */
public enum ImportRefusal {
  IMPORT_ALREADY_RUNNING,
  ORGANO_NOT_ELIGIBLE
}
