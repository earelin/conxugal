package gal.conxugal.application.rest.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Driven through a real decoder over real JSON, because what is under test is the difference
 * between one JSON type and another — and a stubbed decoder would be free to report whichever
 * difference the test expected. It is also the only way to pin the distinction the reader
 * rests on, that a property present and null is not a property absent.
 */
class StrictBodyTest {

  private static final Argument<?> TYPE = Argument.of(Object.class);
  private static final String UUID_TEXT = "1c9e4d2b-7a3f-4e58-9b21-0d6f5a4c3e77";

  private enum Sample {
    FIRST,
    SECOND
  }

  // --- read ------------------------------------------------------------------

  @Test
  void reads_json_object() throws IOException {
    StrictBody body = read(
        """
        {"name":"Sanidade"}\
        """);

    assertThat(body.requiredString("name")).isEqualTo("Sanidade");
  }

  @Test
  void refuses_body_that_is_an_array() {
    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> read("[]"))
        .withMessageContaining("a JSON object");
  }

  @Test
  void refuses_body_that_is_bare_scalar() {
    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> read(
            """
            "Sanidade"\
            """));
  }

  @Test
  void refuses_body_that_is_null() {
    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> StrictBody.read(nullNodeDecoder(), TYPE))
        .withMessageContaining("a JSON object");
  }

  // --- requiredString --------------------------------------------------------

  @Test
  void refuses_string_property_that_is_absent() throws IOException {
    StrictBody body = read("{}");

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredString("name"))
        .withMessageContaining("'name'");
  }

  @Test
  void refuses_string_property_that_is_null() throws IOException {
    StrictBody body = read(
        """
        {"name":null}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredString("name"));
  }

  /** The coercion that renamed a term to the text "false" rather than refusing the request. */
  @Test
  void refuses_boolean_where_string_is_declared() throws IOException {
    StrictBody body = read(
        """
        {"name":false}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredString("name"))
        .withMessageContaining("a JSON string");
  }

  @Test
  void refuses_number_where_string_is_declared() throws IOException {
    StrictBody body = read(
        """
        {"name":42}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredString("name"));
  }

  /** Blankness is the edge's rule to apply, not the reader's to anticipate. */
  @Test
  void keeps_an_empty_string() throws IOException {
    StrictBody body = read(
        """
        {"name":""}\
        """);

    assertThat(body.requiredString("name")).isEmpty();
  }

  // --- requiredBoolean -------------------------------------------------------

  @Test
  void reads_boolean_property() throws IOException {
    StrictBody body = read(
        """
        {"enabled":true}\
        """);

    assertThat(body.requiredBoolean("enabled")).isTrue();
  }

  /** The coercion that read an administrator's request as the opposite of what it said. */
  @Test
  void refuses_string_where_boolean_is_declared() throws IOException {
    StrictBody body = read(
        """
        {"enabled":"AAA"}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredBoolean("enabled"))
        .withMessageContaining("a JSON boolean");
  }

  @Test
  void refuses_boolean_property_that_is_absent() throws IOException {
    StrictBody body = read("{}");

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredBoolean("enabled"));
  }

  // --- requiredUuid ----------------------------------------------------------

  @Test
  void reads_uuid_property() throws IOException {
    StrictBody body = read(
        """
        {"termoId":"%s"}\
        """.formatted(UUID_TEXT));

    assertThat(body.requiredUuid("termoId")).isEqualTo(UUID.fromString(UUID_TEXT));
  }

  @Test
  void refuses_string_that_is_not_uuid() throws IOException {
    StrictBody body = read(
        """
        {"termoId":"not-a-uuid"}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredUuid("termoId"))
        .withMessageContaining("a UUID");
  }

  @Test
  void refuses_object_where_uuid_is_declared() throws IOException {
    StrictBody body = read(
        """
        {"termoId":{}}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredUuid("termoId"));
  }

  @Test
  void refuses_uuid_property_that_is_null() throws IOException {
    StrictBody body = read(
        """
        {"termoId":null}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredUuid("termoId"));
  }

  // --- requiredNullableUuid --------------------------------------------------

  /** Null is a value here — it is how a move says "the root". */
  @Test
  void reads_explicit_null_as_nullable_uuid() throws IOException {
    StrictBody body = read(
        """
        {"parentId":null}\
        """);

    assertThat(body.requiredNullableUuid("parentId")).isNull();
  }

  @Test
  void reads_present_nullable_uuid() throws IOException {
    StrictBody body = read(
        """
        {"parentId":"%s"}\
        """.formatted(UUID_TEXT));

    assertThat(body.requiredNullableUuid("parentId")).isEqualTo(UUID.fromString(UUID_TEXT));
  }

  /** Omitting it states nothing, and must not read as the explicit null above. */
  @Test
  void refuses_nullable_uuid_that_is_absent() throws IOException {
    StrictBody body = read("{}");

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredNullableUuid("parentId"))
        .withMessageContaining("'parentId'");
  }

  // --- optionalUuid ----------------------------------------------------------

  @Test
  void reads_absent_optional_uuid_as_null() throws IOException {
    StrictBody body = read("{}");

    assertThat(body.optionalUuid("parentId")).isNull();
  }

  @Test
  void reads_explicit_null_optional_uuid_as_null() throws IOException {
    StrictBody body = read(
        """
        {"parentId":null}\
        """);

    assertThat(body.optionalUuid("parentId")).isNull();
  }

  @Test
  void refuses_optional_uuid_that_is_not_uuid() throws IOException {
    StrictBody body = read(
        """
        {"parentId":"not-a-uuid"}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.optionalUuid("parentId"));
  }

  // --- requiredEnum ----------------------------------------------------------

  @Test
  void reads_enum_property() throws IOException {
    StrictBody body = read(
        """
        {"role":"SECOND"}\
        """);

    assertThat(body.requiredEnum("role", Sample.class)).isEqualTo(Sample.SECOND);
  }

  @Test
  void refuses_value_outside_the_enum() throws IOException {
    StrictBody body = read(
        """
        {"role":"WIZARD"}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredEnum("role", Sample.class))
        .withMessageContaining("FIRST");
  }

  @Test
  void refuses_enum_property_that_is_not_string() throws IOException {
    StrictBody body = read(
        """
        {"role":7}\
        """);

    assertThatExceptionOfType(InvalidFormatException.class)
        .isThrownBy(() -> body.requiredEnum("role", Sample.class));
  }

  private static StrictBody read(String json) throws IOException {
    return StrictBody.read(decoderOf(json), TYPE);
  }

  private static Decoder decoderOf(String json) throws IOException {
    JsonNode node = ObjectMapper.getDefault().readValue(json, Argument.of(JsonNode.class));
    return JsonNodeDecoder.create(node, LimitingStream.DEFAULT_LIMITS);
  }

  /**
   * A literal {@code null} body, built from the node rather than parsed: the mapper answers a
   * Java null for it, which never reaches the reader under test.
   */
  private static Decoder nullNodeDecoder() {
    return JsonNodeDecoder.create(JsonNode.nullNode(), LimitingStream.DEFAULT_LIMITS);
  }
}
