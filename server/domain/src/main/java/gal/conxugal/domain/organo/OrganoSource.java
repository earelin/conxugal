package gal.conxugal.domain.organo;

import java.util.List;

/**
 * Port for retrieving the published list of Órganos de Contratación. Implemented by an adapter in
 * the {@code infrastructure} module.
 *
 * <p>Always returns the full list, or throws {@link OrganoSourceUnavailableException} — never a
 * partial or empty success, so a reconciliation use case can never mistake a source failure for a
 * catalogue that has genuinely shrunk to nothing.
 */
public interface OrganoSource {

  List<OrganoSourceEntry> fetchAll();
}
