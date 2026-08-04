package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.application.rest.json.StrictBody;
import gal.conxugal.commons.text.Text;
import gal.conxugal.commons.text.Whitespace;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.UUID;

/**
 * The name for a new term and the term it sits under. A null {@code parentId} is a value
 * rather than an omission — it places the term at the root — so it carries no
 * {@code @NotNull}: rejecting it would make a root term unexpressible. Unlike a move, leaving
 * the field out says the same thing as sending null.
 */
@Serdeable.Deserializable(using = CreateTermoRequest.StrictDeserializer.class)
public record CreateTermoRequest(
    @NotBlank
    @Pattern(regexp = Whitespace.NON_BLANK_PATTERN)
    @Pattern(regexp = Text.NO_NUL_PATTERN)
    @Pattern(regexp = Text.SINGLE_LINE_PATTERN)
    @Size(max = 255)
    String name,
    @Nullable UUID parentId) {

  @Singleton
  static final class StrictDeserializer implements Deserializer<CreateTermoRequest> {

    @Override
    public CreateTermoRequest deserialize(
        Decoder decoder, DecoderContext context, Argument<? super CreateTermoRequest> type)
        throws IOException {
      StrictBody body = StrictBody.read(decoder, type);
      return new CreateTermoRequest(body.requiredString("name"), body.optionalUuid("parentId"));
    }
  }
}
