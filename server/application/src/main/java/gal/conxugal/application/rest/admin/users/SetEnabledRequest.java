package gal.conxugal.application.rest.admin.users;

import gal.conxugal.application.rest.json.StrictBody;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;

/** The desired enabled state for an account. */
@Serdeable.Deserializable(using = SetEnabledRequest.StrictDeserializer.class)
public record SetEnabledRequest(@NotNull Boolean enabled) {

  @Singleton
  static final class StrictDeserializer implements Deserializer<SetEnabledRequest> {

    @Override
    public SetEnabledRequest deserialize(
        Decoder decoder, DecoderContext context, Argument<? super SetEnabledRequest> type)
        throws IOException {
      return new SetEnabledRequest(StrictBody.read(decoder, type).requiredBoolean("enabled"));
    }
  }
}
