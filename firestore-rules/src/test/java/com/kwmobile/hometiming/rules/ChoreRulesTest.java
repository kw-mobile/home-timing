package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.junit.Before;
import org.junit.Test;

/** chores and logs (docs/DATA_MODEL.md, docs/MVP.md 완료·반복 계약). Writes mirror ChoreRepository. */
public class ChoreRulesTest extends RulesTestBase {
    private static final String HOUSE = "house-a";
    private static final String CHORE = "wash";
    private static final Auth OWNER = Auth.user("alice");
    private static final Auth MEMBER = Auth.user("bob");
    private static final Auth OUTSIDER = Auth.user("carol");
    private static final int SATURDAY = 6;
    private static final int SUNDAY = 7;

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    /** The next Saturday strictly after today. */
    private static final LocalDate NEXT_SAT = TODAY.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    /** A Saturday safely in the past. */
    private static final LocalDate OLD_SAT = NEXT_SAT.minusDays(28);

    /** One chore document's fields, changed per test. */
    private static final class Doc {
        String createdBy = "bob";
        String updatedBy = "bob";
        String name = "침구 세탁";
        String type = "BEDDING_WASH";
        boolean weatherSensitive = true;
        int weekday = SATURDAY;
        LocalDate planned = NEXT_SAT;
        LocalDate next = NEXT_SAT;
        String occurrence = "occ-1";
        int revision = 1;
        String recordId;
        int recordCount;
        String dismissed;
        LocalDate lastDone;

        Fields fields() {
            Fields fields = Fields.of()
                    .str("name", name).str("type", type).bool("weatherSensitive", weatherSensitive)
                    .num("weekday", weekday)
                    .str("plannedDueDate", planned.toString()).str("nextDueDate", next.toString())
                    .str("occurrenceId", occurrence).num("revision", revision)
                    .str("createdBy", createdBy).str("updatedBy", updatedBy)
                    .num("recordCount", recordCount).bool("deleting", false);
            if (recordId != null) fields.str("recordMutationId", recordId);
            if (dismissed != null) {
                fields.str("dismissedOccurrenceId", dismissed);
            }
            if (lastDone != null) {
                fields.str("lastDoneDate", lastDone.toString());
            }
            return fields;
        }
    }

    private static String chorePath(String id) {
        return "houses/" + HOUSE + "/chores/" + id;
    }

    private static String logPath(String logId) {
        return chorePath(CHORE) + "/logs/" + logId;
    }

    private static void seed(Doc doc) {
        firestore.seed(chorePath(CHORE), doc.fields().time("createdAt", SEEDED_AT).time("updatedAt", SEEDED_AT));
    }

    /** Whole-document write with the stored createdAt, as the repository does. */
    private static Write update(Doc doc, String actor) {
        doc.updatedBy = actor;
        if (doc.recordId == null && !doc.next.equals(doc.planned)) {
            doc.recordId = "moved_" + doc.occurrence + "_" + doc.revision;
            doc.recordCount = 1;
        }
        return Write.set(chorePath(CHORE), doc.fields().time("createdAt", SEEDED_AT)).serverTime("updatedAt");
    }

    private static Write[] create(String id, Doc doc) {
        return new Write[] {
            Write.create(chorePath(id), doc.fields()).serverTime("createdAt").serverTime("updatedAt"),
            Write.merge("houses/" + HOUSE, Fields.of().str("choreMutationId", id)).increment("choreCount", 1)
        };
    }

    private static Write doneLog(String occurrence, LocalDate date, String actor) {
        return Write.create(logPath("done_" + occurrence), Fields.of()
                .str("action", "DONE").str("occurrenceId", occurrence).str("date", date.toString())
                .str("actorId", actor).str("actorName", nameOf(actor)))
                .serverTime("createdAt");
    }

    private static Write movedLog(String occurrence, int revision, LocalDate from, LocalDate to, String actor) {
        return Write.create(logPath("moved_" + occurrence + "_" + revision), Fields.of()
                .str("action", "RESCHEDULED").str("occurrenceId", occurrence).str("date", to.toString())
                .str("fromDate", from.toString()).str("reason", "RAIN_FORECAST")
                .str("actorId", actor).str("actorName", nameOf(actor)))
                .serverTime("createdAt");
    }

    /** The correct next occurrence: from the week after planned, the first one after done. */
    private static LocalDate nextPlanned(LocalDate planned, LocalDate done) {
        LocalDate next = planned.plusDays(7);
        while (!next.isAfter(done)) {
            next = next.plusDays(7);
        }
        return next;
    }

    private static Doc completed(Doc before, LocalDate done, LocalDate next) {
        Doc after = new Doc();
        after.planned = next;
        after.next = next;
        after.occurrence = "occ-2";
        after.revision = before.revision + 1;
        after.lastDone = done;
        after.recordCount = before.recordCount + 1;
        after.recordId = "done_" + before.occurrence;
        return after;
    }

    @Before
    public void seedHouses() {
        seedHouse(HOUSE, 0, "alice", "bob");
        seedHouse("house-c", 0, "carol");
        assertAllowed(firestore.commit(Auth.admin(),
                Write.merge("houses/" + HOUSE, Fields.of().num("choreCount", 1))));
        seed(new Doc());
    }

    // ---- Read and register ----

    @Test
    public void membersReadChoresOutsidersDoNot() {
        assertAllowed(firestore.get(OWNER, chorePath(CHORE)));
        assertDenied(firestore.get(OUTSIDER, chorePath(CHORE)));
    }

    @Test
    public void memberRegistersWithCountIncrement() {
        Doc doc = new Doc();
        doc.createdBy = "alice";
        doc.updatedBy = "alice";

        assertAllowed(firestore.commit(OWNER, create("clean", doc)));
    }

    @Test
    public void registrationNeedsCountAndMembership() {
        Doc doc = new Doc();
        doc.createdBy = "alice";
        doc.updatedBy = "alice";
        assertDenied(firestore.commit(OWNER, create("clean", doc)[0]));

        Doc outsider = new Doc();
        outsider.createdBy = "carol";
        outsider.updatedBy = "carol";
        assertDenied(firestore.commit(OUTSIDER, create("clean", outsider)));
    }

    @Test
    public void weekdayMustMatchTheDate() {
        Doc doc = new Doc();
        doc.createdBy = "alice";
        doc.updatedBy = "alice";
        doc.weekday = SUNDAY;

        assertDenied(firestore.commit(OWNER, create("clean", doc)));
    }

    @Test
    public void malformedChoresAreRejected() {
        Doc badType = new Doc();
        badType.createdBy = "alice";
        badType.updatedBy = "alice";
        badType.type = "GARDEN";
        assertDenied(firestore.commit(OWNER, create("a", badType)));

        Doc badWeekday = new Doc();
        badWeekday.createdBy = "alice";
        badWeekday.updatedBy = "alice";
        badWeekday.weekday = 8;
        assertDenied(firestore.commit(OWNER, create("b", badWeekday)));

        Doc badRevision = new Doc();
        badRevision.createdBy = "alice";
        badRevision.updatedBy = "alice";
        badRevision.revision = 2;
        assertDenied(firestore.commit(OWNER, create("c", badRevision)));

        Doc moved = new Doc();
        moved.createdBy = "alice";
        moved.updatedBy = "alice";
        moved.next = NEXT_SAT.plusDays(1);
        assertDenied(firestore.commit(OWNER, create("d", moved)));

        Doc dismissed = new Doc();
        dismissed.createdBy = "alice";
        dismissed.updatedBy = "alice";
        dismissed.dismissed = "occ-1";
        assertDenied(firestore.commit(OWNER, create("e", dismissed)));
    }

    // ---- Edit ----

    @Test
    public void displayEditKeepsRevision() {
        Doc doc = new Doc();
        doc.name = "이불 빨래";

        assertAllowed(firestore.commit(OWNER, update(doc, "alice")));
    }

    @Test
    public void revisionCannotMoveWithoutScheduleChange() {
        Doc doc = new Doc();
        doc.revision = 2;

        assertDenied(firestore.commit(OWNER, update(doc, "alice")));
    }

    @Test
    public void weekdayEditRestartsOnTheNewWeekday() {
        Doc doc = new Doc();
        doc.weekday = SUNDAY;
        doc.planned = NEXT_SAT.plusDays(1);
        doc.next = NEXT_SAT.plusDays(1);
        doc.revision = 2;

        assertAllowed(firestore.commit(OWNER, update(doc, "alice")));
    }

    @Test
    public void weekdayEditMustLandOnThatWeekday() {
        Doc doc = new Doc();
        doc.weekday = SUNDAY;
        doc.revision = 2;

        assertDenied(firestore.commit(OWNER, update(doc, "alice")));
    }

    @Test
    public void ownershipCannotBeRewritten() {
        Doc doc = new Doc();
        doc.createdBy = "alice";

        assertDenied(firestore.commit(OWNER, update(doc, "alice")));
    }

    // ---- Moving this occurrence ----

    @Test
    public void movingKeepsOccurrenceAndOriginalDateWithALog() {
        Doc doc = new Doc();
        doc.next = NEXT_SAT.plusDays(1);
        doc.revision = 2;

        assertAllowed(firestore.commit(OWNER, update(doc, "alice"),
                movedLog("occ-1", 2, NEXT_SAT, doc.next, "alice")));
    }

    @Test
    public void movingNeedsItsLog() {
        Doc doc = new Doc();
        doc.next = NEXT_SAT.plusDays(1);
        doc.revision = 2;

        assertDenied(firestore.commit(OWNER, update(doc, "alice")));
    }

    @Test
    public void cannotMoveToThePast() {
        Doc doc = new Doc();
        doc.next = TODAY.minusDays(1);
        doc.revision = 2;

        assertDenied(firestore.commit(OWNER, update(doc, "alice"),
                movedLog("occ-1", 2, NEXT_SAT, doc.next, "alice")));
    }

    @Test
    public void movingCannotChangeTheOriginalDate() {
        Doc doc = new Doc();
        doc.planned = NEXT_SAT.plusDays(7);
        doc.next = NEXT_SAT.plusDays(7);
        doc.revision = 2;

        assertDenied(firestore.commit(OWNER, update(doc, "alice"),
                movedLog("occ-1", 2, NEXT_SAT, doc.next, "alice")));
    }

    @Test
    public void moveLogMustRecordTheRealDates() {
        Doc doc = new Doc();
        doc.next = NEXT_SAT.plusDays(1);
        doc.revision = 2;

        assertDenied(firestore.commit(OWNER, update(doc, "alice"),
                movedLog("occ-1", 2, NEXT_SAT.plusDays(3), doc.next, "alice")));
    }

    // ---- Dismissing a suggestion ----

    @Test
    public void dismissingRecordsTheCurrentOccurrence() {
        Doc doc = new Doc();
        doc.dismissed = "occ-1";
        doc.revision = 2;

        assertAllowed(firestore.commit(OWNER, update(doc, "alice")));
    }

    @Test
    public void dismissingAnotherOccurrenceOrWithoutRevisionIsDenied() {
        Doc other = new Doc();
        other.dismissed = "occ-9";
        other.revision = 2;
        assertDenied(firestore.commit(OWNER, update(other, "alice")));

        Doc noBump = new Doc();
        noBump.dismissed = "occ-1";
        assertDenied(firestore.commit(OWNER, update(noBump, "alice")));
    }

    // ---- Completing ----

    private static Doc seedOverdue() {
        Doc before = new Doc();
        before.planned = OLD_SAT;
        before.next = OLD_SAT;
        seed(before);
        return before;
    }

    @Test
    public void lateCompletionGoesToTheFirstWeekdayAfterDoneSkippingMissedWeeks() {
        Doc before = seedOverdue();
        Doc after = completed(before, TODAY, nextPlanned(OLD_SAT, TODAY));

        assertAllowed(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", TODAY, "alice")));
    }

    @Test
    public void earlyCompletionGoesToNextWeek() {
        Doc before = new Doc();
        Doc after = completed(before, TODAY, NEXT_SAT.plusDays(7));

        assertAllowed(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", TODAY, "alice")));
    }

    @Test
    public void movedThenCompletedKeepsTheOriginalWeekday() {
        Doc before = new Doc();
        before.planned = OLD_SAT;
        before.next = OLD_SAT.plusDays(1);
        before.revision = 2;
        seed(before);
        LocalDate done = OLD_SAT.plusDays(1);
        Doc after = completed(before, done, OLD_SAT.plusDays(7));

        assertAllowed(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", done, "alice")));
    }

    @Test
    public void nextOccurrenceMustBeTheFirstOneAfterDone() {
        Doc before = seedOverdue();
        LocalDate correct = nextPlanned(OLD_SAT, TODAY);

        assertDenied(firestore.commit(OWNER, update(completed(before, TODAY, correct.plusDays(7)), "alice"),
                doneLog("occ-1", TODAY, "alice")));
        assertDenied(firestore.commit(OWNER, update(completed(before, TODAY, correct.minusDays(7)), "alice"),
                doneLog("occ-1", TODAY, "alice")));
        assertDenied(firestore.commit(OWNER, update(completed(before, TODAY, correct.plusDays(1)), "alice"),
                doneLog("occ-1", TODAY, "alice")));
    }

    @Test
    public void doneDateCannotBeInTheFuture() {
        Doc before = new Doc();
        LocalDate tomorrow = TODAY.plusDays(1);
        Doc after = completed(before, tomorrow, nextPlanned(NEXT_SAT, tomorrow));

        assertDenied(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", tomorrow, "alice")));
    }

    @Test
    public void completionNeedsItsLogAndOnlyOnce() {
        Doc before = seedOverdue();
        Doc after = completed(before, TODAY, nextPlanned(OLD_SAT, TODAY));

        assertDenied(firestore.commit(OWNER, update(after, "alice")));
        assertAllowed(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", TODAY, "alice")));

        // A second device still showing occ-1 cannot complete it again.
        assertDenied(firestore.commit(MEMBER, doneLog("occ-1", TODAY, "bob")));
    }

    @Test
    public void logMustMatchTheActor() {
        Doc before = seedOverdue();
        Doc after = completed(before, TODAY, nextPlanned(OLD_SAT, TODAY));

        assertDenied(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", TODAY, "bob")));
    }

    @Test
    public void logsCannotBeEdited() {
        Doc before = seedOverdue();
        Doc after = completed(before, TODAY, nextPlanned(OLD_SAT, TODAY));
        assertAllowed(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", TODAY, "alice")));

        assertDenied(firestore.commit(OWNER, Write.merge(logPath("done_occ-1"),
                Fields.of().str("date", TODAY.minusDays(1).toString()))));
    }

    @Test
    public void usedAndUnsafeNextOccurrencesAreRejectedWithoutBreakingTheCurrentChore() {
        Doc before = seedOverdue();
        Doc after = completed(before, TODAY, nextPlanned(OLD_SAT, TODAY));
        assertAllowed(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", TODAY, "alice")));
        Doc next = completed(after, TODAY, nextPlanned(after.planned, TODAY));
        next.occurrence = "occ-1";
        assertDenied(firestore.commit(OWNER, update(next, "alice"), doneLog("occ-2", TODAY, "alice")));
        next.occurrence = "bad/id";
        assertDenied(firestore.commit(OWNER, update(next, "alice"), doneLog("occ-2", TODAY, "alice")));
        next.occurrence = "occ-3";
        assertAllowed(firestore.commit(OWNER, update(next, "alice"), doneLog("occ-2", TODAY, "alice")));
    }

    // ---- Deleting ----

    private static Write[] deleteChore() {
        return new Write[] {
            Write.delete(chorePath(CHORE)),
            Write.merge("houses/" + HOUSE, Fields.of().str("choreMutationId", CHORE)).increment("choreCount", -1)
        };
    }

    @Test
    public void registrantDeletesWithLogsAndCount() {
        Doc before = seedOverdue();
        Doc after = completed(before, TODAY, nextPlanned(OLD_SAT, TODAY));
        assertAllowed(firestore.commit(OWNER, update(after, "alice"), doneLog("occ-1", TODAY, "alice")));

        assertAllowed(firestore.commit(MEMBER, startDeleting(chorePath(CHORE), "bob")));
        assertAllowed(firestore.commit(MEMBER, deleteRecord(chorePath(CHORE), "logs", "done_occ-1", "bob")));
        assertAllowed(firestore.commit(MEMBER, deleteChore()));
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), chorePath(CHORE)).status());
    }

    @Test
    public void ownerMayDeleteOthersChores() {
        assertAllowed(firestore.commit(OWNER, startDeleting(chorePath(CHORE), "alice")));
        assertAllowed(firestore.commit(OWNER, deleteChore()));
    }

    @Test
    public void otherMemberCannotDelete() {
        Doc alices = new Doc();
        alices.createdBy = "alice";
        alices.updatedBy = "alice";
        seed(alices);

        assertDenied(firestore.commit(MEMBER, deleteChore()));
    }

    @Test
    public void deleteNeedsTheCountDecrement() {
        assertDenied(firestore.commit(OWNER, deleteChore()[0]));
    }

    @Test
    public void countCannotJumpByMoreThanOne() {
        assertDenied(firestore.commit(OWNER, Write.merge("houses/" + HOUSE, Fields.of()).increment("choreCount", 2)));
    }
}
