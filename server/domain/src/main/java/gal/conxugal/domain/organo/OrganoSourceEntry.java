package gal.conxugal.domain.organo;

import java.util.Objects;

/**
 * One Órgano de Contratación as published by the source, before it becomes an
 * {@link OrganoDeContratacion}. {@code sourceKey} is the same stable key reconciliation matches
 * on.
 */
public record OrganoSourceEntry(String sourceKey, String name) {

  public OrganoSourceEntry {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    Objects.requireNonNull(name, "name must not be null");
  }
}
