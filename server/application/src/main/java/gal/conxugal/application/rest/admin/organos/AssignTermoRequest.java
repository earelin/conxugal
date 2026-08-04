package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.application.rest.json.StrictBody;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.UUID;

/**
 * The term to file an Órgano under. Unlike a term's parent, this one is required: an Órgano
 * is returned to the unclassified set by deleting the placement, not by assigning a null.
 */
@Serdeable.Deserializable(using = AssignTermoRequest.StrictDeserializer.class)
public record AssignTermoRequest(@NotNull UUID termoId) {

  @Singleton
  static final class StrictDeserializer implements Deserializer<AssignTermoRequest> {

    @Override
    public AssignTermoRequest deserialize(
        Decoder decoder, DecoderContext context, Argument<? super AssignTermoRequest> type)
        throws IOException {
      return new AssignTermoRequest(StrictBody.read(decoder, type).requiredUuid("termoId"));
    }
  }
}
