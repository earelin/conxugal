package gal.conxugal.domain.contrato;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import java.time.LocalDate;

/**
 * How far back a contratos menores walk may go before it gives up. The walk ends when an Órgano's
 * stored count reaches the count the source reports for it; the floor is the backstop for the case
 * where the two never converge, so a walk cannot step backwards forever.
 *
 * <p>2018 because that is where the source's published history begins. Reaching the floor without
 * matching the count leaves the Órgano <em>incomplete</em> rather than silently complete, so it is
 * resumed later rather than treated as loaded — which is why an approximate floor is safe: setting
 * it too early costs empty windows, and setting it too late is visible as an import that never
 * completes.
 *
 * <p>The three-month window and the hundred-row page are <em>not</em> here and are not
 * configurable. They are the source's own measured limits, and the page is also the batch a run
 * advances after, so tying them to configuration would make the abandonment bound negotiable
 * against a figure the source decides.
 */
@ConfigurationProperties(ContratosMenoresImportConfiguration.PREFIX)
public record ContratosMenoresImportConfiguration(
    @Bindable(defaultValue = "2018-01-01") LocalDate historyFloor) {

  static final String PREFIX = "conxugal.contratos-menores.import";

  public ContratosMenoresImportConfiguration {
    if (historyFloor == null) {
      // Without a floor the walk has no backstop at all: an Órgano whose count never converges
      // would step backwards a quarter at a time for as long as the process lived.
      throw new IllegalArgumentException("history-floor must be set");
    }
  }
}
