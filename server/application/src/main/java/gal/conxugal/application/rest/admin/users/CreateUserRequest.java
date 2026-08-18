package gal.conxugal.application.rest.admin.users;

import gal.conxugal.application.rest.json.StrictBody;
import gal.conxugal.domain.user.Role;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;

/** The email and role for a new account; the server generates the initial password. */
@Serdeable.Deserializable(using = CreateUserRequest.StrictDeserializer.class)
public record CreateUserRequest(
    @NotBlank
    @Pattern(regexp = EMAIL_PATTERN)
    @Size(max = 254)
    String email,
    @NotNull Role role) {

  /**
   * The same characters as the contract's {@code pattern} for this field, which is the rule:
   * {@code format: email} asserts nothing on its own, and the laxer reading it invited is what
   * let through an address no client would call one. Every character rule is written as a
   * literal range — no {@code \s}, {@code \d} or {@code \w} — because those three are read
   * differently by the contract's regex dialect than by this one, and the two must reach the
   * same verdict on every input; the single lookahead is a length bound, not a character rule.
   * The RFC 5321 limits belong to the rule rather than sitting outside it for the same reason:
   * a reader honouring {@code format} enforces 64 for the local part and 63 for a domain
   * label, so a pattern that allowed more would contradict the format beside it. Both halves
   * must change together.
   */
  private static final String EMAIL_PATTERN =
      "^(?=[^@]{1,64}@)[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
          + "(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
          + "@([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$";

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
