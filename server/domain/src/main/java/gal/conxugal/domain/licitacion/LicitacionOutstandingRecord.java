package gal.conxugal.domain.licitacion;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One procedure an Órgano's walk could not retrieve or parse, held so that a later run can come
 * back to it. Identified by what the source published it under, and carrying the four values the
 * record itself cannot answer for.
 *
 * <p><strong>Those four are here because of a gap that only shows up on the retry path.</strong>
 * The publication date, the last-modified date and both halves of the state come from the listing
 * entry rather than from the record — the record publishes the state's label alone, which for the
 * two codes sharing one is ambiguous. A retry happens before the cursor resumes, with no listing
 * entry in hand and no way to ask the source for a single row, so a retried procedure that did not
 * keep these could not be stored at all.
 *
 * <p><strong>The state is its two published values, not a reference.</strong> This records what the
 * listing said; the vocabulary row it will need may not exist yet, and pointing at one would make a
 * ledger entry depend on a write it does not perform. The retry upserts the state from these two,
 * exactly as the listing path does.
 *
 * <p>It carries no Órgano: which Órgano an entry belongs to is the ledger's to file it under, and
 * every operation takes one alongside. Two entries are therefore the same entry when they say the
 * same thing about the same publication.
 *
 * <p>The label is held the way {@link LicitacionState} holds it, so that the state a retry upserts
 * from these two values is the state the listing path would have upserted.
 */
public record LicitacionOutstandingRecord(
    PublicationId publicationId,
    @Nullable LocalDate publicationDate,
    @Nullable LocalDate lastModified,
    int stateCode,
    @Nullable String stateLabel) {

  public LicitacionOutstandingRecord {
    Objects.requireNonNull(publicationId, "publicationId must not be null");
    stateLabel = PublishedText.orNullWhenBlank(stateLabel);
  }
}
