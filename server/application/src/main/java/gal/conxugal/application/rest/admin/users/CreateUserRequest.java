package gal.conxugal.application.rest.admin.users;

import gal.conxugal.application.rest.json.StrictBody;
import gal.conxugal.domain.user.Role;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;

/** The email and role for a new account; the server generates the initial password. */
@Serdeable.Deserializable(using = CreateUserRequest.StrictDeserializer.class)
public record CreateUserRequest(
    @NotBlank @Email String email,
    @NotNull Role role) {

  @Singleton
  static final class StrictDeserializer implements Deserializer<CreateUserRequest> {

    @Override
    public CreateUserRequest deserialize(
        Decoder decoder, DecoderContext context, Argument<? super CreateUserRequest> type)
        throws IOException {
      StrictBody body = StrictBody.read(decoder, type);
      return new CreateUserRequest(
          body.requiredString("email"), body.requiredEnum("role", Role.class));
    }
  }
}
