package gal.conxugal.application.rest.admin.organos;

/**
 * Thrown by the controller when {@link gal.conxugal.domain.organo.ImportOrganos} reports a
 * failed run, so it surfaces as a 500 rather than a 200 outcome body.
 */
class ImportFailedException extends RuntimeException {
}
