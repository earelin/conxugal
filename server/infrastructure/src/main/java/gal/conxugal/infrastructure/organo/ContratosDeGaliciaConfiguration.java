package gal.conxugal.infrastructure.organo;

import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuration for the {@link ContratosDeGaliciaOrganoSourceAdapter}'s outbound requests. */
@ConfigurationProperties("conxugal.contratosdegalicia")
public record ContratosDeGaliciaConfiguration(String baseUrl) {}
