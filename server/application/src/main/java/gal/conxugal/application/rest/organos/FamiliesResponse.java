package gal.conxugal.application.rest.organos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import gal.conxugal.application.rest.contratosmenores.ContratosMenoresSummaryResponse;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The contract families an Órgano holds visible data for, keyed by the slug the client's
 * child-route segment uses — so nothing here can disagree with the router.
 *
 * <p><b>A family is present when it has visible data and absent when it has none</b>, and that is
 * the whole mechanism. There is no <em>has contracts</em> flag: a boolean beside a summary could
 * disagree with it, an absent property cannot.
 *
 * <p><b>The inclusion here is the opposite of {@link OrganoMemberResponse}'s, deliberately.</b>
 * That record forces every property onto the wire so an Órgano holding nothing still carries a
 * {@code families} key; this one drops a null so an absent family is omitted rather than sent as
 * an explicit null. An absent family and a family that is null would be two spellings of one fact,
 * and the contract declares only the first.
 *
 * <p>A family the system gains later adds a component here and nothing else: no existing property
 * changes, and this record never learns what any summary contains.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FamiliesResponse(
    @JsonProperty("contratos-menores") @Nullable
        ContratosMenoresSummaryResponse contratosMenores) {
}
