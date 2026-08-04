package gal.conxugal.application.rest.json;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.exceptions.InvalidFormatException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * A request body read as the contract declares it, refusing what the mapper would otherwise
 * bend into shape.
 *
 * <p>Left to itself the mapper coerces between JSON types and treats an absent property as a
 * null one, so {@code {"enabled": "AAA"}} arrives as {@code false} and {@code {}} arrives as a
 * complete object. Both are bodies the contract forbids, and neither is refused — the second
 * silently, the first as the opposite of what the caller wrote. Reading the body as its plain
 * JSON shape first makes the declared type and the declared presence checkable.
 */
public final class StrictBody {

  private final Argument<?> type;
  private final Map<?, ?> properties;

  private StrictBody(Argument<?> type, Map<?, ?> properties) {
    this.type = type;
    this.properties = properties;
  }

  public static StrictBody read(Decoder decoder, Argument<?> type) throws IOException {
    Object body = decoder.decodeArbitrary();
    if (body instanceof Map<?, ?> object) {
      return new StrictBody(type, object);
    }
    throw invalid(type, "a JSON object", body);
  }

  public String requiredString(String property) throws IOException {
    Object value = present(property);
    if (value instanceof String string) {
      return string;
    }
    throw invalid(type, "a JSON string at '%s'".formatted(property), value);
  }

  public Boolean requiredBoolean(String property) throws IOException {
    Object value = present(property);
    if (value instanceof Boolean bool) {
      return bool;
    }
    throw invalid(type, "a JSON boolean at '%s'".formatted(property), value);
  }

  public UUID requiredUuid(String property) throws IOException {
    return uuid(property, present(property));
  }

  /** A property the contract requires the caller to send, whose value may be null. */
  public UUID requiredNullableUuid(String property) throws IOException {
    Object value = present(property);
    return value == null ? null : uuid(property, value);
  }

  /** A property the contract leaves out entirely, which reads the same as an explicit null. */
  public UUID optionalUuid(String property) throws IOException {
    Object value = properties.get(property);
    return value == null ? null : uuid(property, value);
  }

  public <E extends Enum<E>> E requiredEnum(String property, Class<E> enumType)
      throws IOException {
    String value = requiredString(property);
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw invalid(type, "one of %s at '%s'"
          .formatted(Arrays.toString(enumType.getEnumConstants()), property), value, e);
    }
  }

  private Object present(String property) throws IOException {
    if (!properties.containsKey(property)) {
      throw invalid(type, "the required property '%s'".formatted(property), null);
    }
    return properties.get(property);
  }

  private UUID uuid(String property, Object value) throws IOException {
    if (value instanceof String string) {
      try {
        return UUID.fromString(string);
      } catch (IllegalArgumentException e) {
        throw invalid(type, "a UUID at '%s'".formatted(property), value, e);
      }
    }
    throw invalid(type, "a UUID at '%s'".formatted(property), value);
  }

  private static InvalidFormatException invalid(Argument<?> type, String expected, Object value) {
    return invalid(type, expected, value, null);
  }

  private static InvalidFormatException invalid(
      Argument<?> type, String expected, Object value, Exception cause) {
    return new InvalidFormatException(
        "%s expects %s".formatted(type.getSimpleName(), expected), cause, value);
  }
}
