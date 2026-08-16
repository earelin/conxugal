package gal.conxugal.application.rest.organos;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The contract families an Órgano holds visible data for, keyed by family. The key identifies a
 * family and nothing more — where its section is mounted travels inside the entry, as a
 * {@code route} the client reads rather than a spelling it has to infer from the key.
 *
 * <p><b>A family is present when it has visible data and absent when it has none</b>, and that is
 * the whole mechanism. There is no <em>has contracts</em> flag: a boolean beside a summary could
 * disagree with it, an absent property cannot.
 *
 * <p><b>An absent family is omitted, never sent as an explicit null</b> — the two would be
 * spellings of one fact, and the contract declares only the first, so a client counting the keys
 * would find a family this Órgano does not hold. The serializer's default {@code NON_EMPTY}
 * inclusion already drops a null, so this needs no {@code @JsonInclude} override to say so; the
 * requirement is guarded by the round trip over HTTP that asserts the key is absent.
 *
 * <p>A family the system gains later adds a component here and nothing else: no existing property
 * changes, and this record never learns what any summary contains.
 */
@Serdeable
public record FamiliesResponse(@Nullable ContratosMenoresFamilyResponse contratosMenores) {
}
