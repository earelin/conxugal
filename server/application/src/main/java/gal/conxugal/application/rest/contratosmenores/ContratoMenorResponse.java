package gal.conxugal.application.rest.contratosmenores;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One contrato menor as a browse read serves it. This family has no detail view, so the row is
 * everything the system holds — and nothing else: it names <b>no awarding Órgano</b>, every row of
 * a list belonging to the Órgano already open, and <b>no operador identity</b>, because nothing
 * consumes one until the operadores read exists and a field no client reads is a promise made
 * early.
 *
 * <p><b>Four values have no absent case on the wire</b> — the date, the amount and the awardee's
 * two — because a contract missing any of them is withheld rather than shown incomplete. That is
 * already true of {@link VisibleContratoMenor}, and repeating it here as non-nullable components
 * is what leaves a client with no branch to write.
 *
 * <p>{@code obxecto} and {@code duration} are the only two that can be absent, and they are sent
 * as explicit nulls: the contract declares both required, and the serializer's default inclusion
 * would otherwise drop the key and make an absent object indistinguishable from a field the server
 * forgot.
 *
 * <p>The typed values stop here, as every other response record stops a typed id: an amount goes
 * out as a number and a fiscal identifier as a string, because a {@code {"value": …}} in either
 * would mean a domain wrapper had leaked onto the wire.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ContratoMenorResponse(
    long sourceId,
    LocalDate publicationDate,
    @Nullable String obxecto,
    BigDecimal amount,
    @Nullable String duration,
    AwardeeResponse awardee,
    String sourceUrl) {

  static ContratoMenorResponse of(
      VisibleContratoMenor contrato, ContratosMenoresPublicationConfiguration publication) {
    return new ContratoMenorResponse(
        contrato.sourceId(),
        contrato.publicationDate(),
        contrato.obxecto(),
        contrato.amount().value(),
        contrato.duration(),
        new AwardeeResponse(contrato.awardeeName(), contrato.awardeeFiscalId().value()),
        publication.urlOf(contrato.sourceId()));
  }

  /**
   * Who was awarded the contract: the operador's single selected name and its one canonical fiscal
   * identifier. It carries no identity of its own — the crossing into an operador is added by the
   * read that gives it somewhere to go, rather than by this one shipping a value nothing follows.
   */
  @Serdeable
  public record AwardeeResponse(String name, String fiscalId) {
  }
}
