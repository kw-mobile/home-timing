package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A replacement item as screens see it (docs/DATA_MODEL.md items). Immutable; build new states with
 * {@link ItemSchedule}. Optional text is "" when not set.
 */
public final class Item {
    public final String id;
    public final String name;
    public final String category;
    public final LocalDate startDate;
    public final int cycleDays;
    public final LocalDate lastReplacedDate;
    public final LocalDate nextDueDate;
    /** Current occurrence; changes only when the item is completed. */
    public final String occurrenceId;
    /** +1 whenever schedule fields change; screens send it back to detect concurrent edits. */
    public final int revision;
    public final String productName;
    public final String location;
    public final String memo;
    public final String createdBy;
    /** True after an actual completion, including one on the start date. */
    public final boolean hasCompleted;

    public Item(String id, String name, String category, LocalDate startDate, int cycleDays,
                LocalDate lastReplacedDate, LocalDate nextDueDate, String occurrenceId, int revision,
                String productName, String location, String memo, String createdBy, boolean hasCompleted) {
        this.hasCompleted = hasCompleted;
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.cycleDays = cycleDays;
        this.lastReplacedDate = Objects.requireNonNull(lastReplacedDate, "lastReplacedDate");
        this.nextDueDate = Objects.requireNonNull(nextDueDate, "nextDueDate");
        this.occurrenceId = Objects.requireNonNull(occurrenceId, "occurrenceId");
        this.revision = revision;
        this.productName = productName == null ? "" : productName;
        this.location = location == null ? "" : location;
        this.memo = memo == null ? "" : memo;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    }

    public long daysUntilDue(LocalDate today) {
        return ReplacementCalculator.daysUntil(nextDueDate, today);
    }

    public DueStatus status(LocalDate today) {
        return ReplacementCalculator.status(nextDueDate, today);
    }

    /** Registrant or house owner may delete (docs/MVP.md 데이터·권한 계약). */
    public boolean canDelete(String uid, String houseOwnerId) {
        return createdBy.equals(uid) || (houseOwnerId != null && houseOwnerId.equals(uid));
    }

    boolean sameSchedule(Item other) {
        return startDate.equals(other.startDate)
                && cycleDays == other.cycleDays
                && lastReplacedDate.equals(other.lastReplacedDate)
                && nextDueDate.equals(other.nextDueDate)
                && occurrenceId.equals(other.occurrenceId);
    }
}
