package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.application.rest.json.StrictBody;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.UUID;

/**
 * The term to move a term under. A null {@code parentId} moves it to the root, which is the
 * whole reason the field is nullable rather than carrying a non-null constraint: a move out of
 * a subtree has no other way to say where it lands. Sending it is required all the same — a
 * body that leaves it out states nothing, and must not be read as a move to the root.
 */
@Serdeable.Deserializable(using = MoveTermoRequest.StrictDeserializer.class)
public record MoveTermoRequest(@Nullable UUID parentId) {

  @Singleton
  static final class StrictDeserializer implements Deserializer<MoveTermoRequest> {

    @Override
    public MoveTermoRequest deserialize(
        Decoder decoder, DecoderContext context, Argument<? super MoveTermoRequest> type)
        throws IOException {
      return new MoveTermoRequest(StrictBody.read(decoder, type).requiredNullableUuid("parentId"));
    }
  }
}
