package gal.conxugal.infrastructure.organo;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import java.time.Duration;

/** Configuration for the {@link ContratosDeGaliciaOrganoSourceAdapter}'s outbound requests. */
@ConfigurationProperties("conxugal.contratosdegalicia")
public record ContratosDeGaliciaConfiguration(
    String baseUrl, @Bindable(defaultValue = "10s") Duration readTimeout) {}
