package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.application.rest.json.StrictBody;
import gal.conxugal.commons.text.Text;
import gal.conxugal.commons.text.Whitespace;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;

/** A term's replacement name. */
@Serdeable.Deserializable(using = RenameTermoRequest.StrictDeserializer.class)
public record RenameTermoRequest(
    @NotBlank
    @Pattern(regexp = Whitespace.NON_BLANK_PATTERN)
    @Pattern(regexp = Text.NO_NUL_PATTERN)
    @Pattern(regexp = Text.SINGLE_LINE_PATTERN)
    @Size(max = 255)
    String name) {

  @Singleton
  static final class StrictDeserializer implements Deserializer<RenameTermoRequest> {

    @Override
    public RenameTermoRequest deserialize(
        Decoder decoder, DecoderContext context, Argument<? super RenameTermoRequest> type)
        throws IOException {
      return new RenameTermoRequest(StrictBody.read(decoder, type).requiredString("name"));
    }
  }
}
