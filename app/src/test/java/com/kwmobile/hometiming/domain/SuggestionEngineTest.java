package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SuggestionEngineTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate SAT = LocalDate.of(2026, 11, 7);
    private static final LocalDate SUN = LocalDate.of(2026, 11, 8);

    private static Chore chore(boolean sensitive, LocalDate due) {
        return new Chore("wash", "침구 세탁", "BEDDING_WASH", sensitive, 6, due, due, "occ-1", 3, null, null, "", "", "alice");
    }

    private static DailyForecast day(LocalDate date, int pop, double reh) {
        return new DailyForecast(date, true, pop, false, reh);
    }

    private static Map<LocalDate, DailyForecast> forecast(DailyForecast... days) {
        Map<LocalDate, DailyForecast> map = new HashMap<>();
        for (DailyForecast day : days) {
            map.put(day.date, day);
        }
        return map;
    }

    private static List<Suggestion> suggest(Chore chore, Map<LocalDate, DailyForecast> days) {
        return SuggestionEngine.suggest(Collections.singletonList(chore), days, TODAY);
    }

    @Test
    public void rainySaturdayProposesSunday() {
        List<Suggestion> cards = suggest(chore(true, SAT), forecast(day(SAT, 70, 60), day(SUN, 10, 45)));

        assertEquals(1, cards.size());
        Suggestion card = cards.get(0);
        assertEquals(Suggestion.Kind.MOVE, card.kind);
        assertEquals(SAT, card.from);
        assertEquals(SUN, card.target);
        assertEquals(70, card.fromForecast.maxPop);
        assertEquals(45.0, card.targetForecast.avgReh, 0.001);
    }

    @Test
    public void cardCarriesOccurrenceAndRevision() {
        Suggestion card = suggest(chore(true, SAT), forecast(day(SAT, 70, 60), day(SUN, 10, 45))).get(0);

        assertEquals("wash", card.choreId);
        assertEquals("occ-1", card.occurrenceId);
        assertEquals(3, card.revision);
    }

    @Test
    public void noSuitableDayAdvisesIndoorDrying() {
        Suggestion card = suggest(chore(true, SAT), forecast(day(SAT, 70, 60), day(SUN, 80, 60))).get(0);

        assertEquals(Suggestion.Kind.INDOOR, card.kind);
        assertNull(card.target);
    }

    @Test
    public void cautionIsANoticeOnly() {
        Suggestion card = suggest(chore(true, SAT), forecast(day(SAT, 40, 60), day(SUN, 10, 45))).get(0);

        assertEquals(Suggestion.Kind.CAUTION, card.kind);
        assertNull(card.target);
    }

    @Test
    public void goodDayHasNoCard() {
        assertTrue(suggest(chore(true, SAT), forecast(day(SAT, 10, 50))).isEmpty());
    }

    @Test
    public void notWeatherSensitiveHasNoCard() {
        assertTrue(suggest(chore(false, SAT), forecast(day(SAT, 90, 90), day(SUN, 10, 45))).isEmpty());
    }

    @Test
    public void outOfForecastRangeHasNoCard() {
        assertTrue(suggest(chore(true, SAT.plusDays(7)), forecast(day(SAT, 90, 90))).isEmpty());
    }

    @Test
    public void unknownForecastHasNoCard() {
        Map<LocalDate, DailyForecast> days = new HashMap<>();
        days.put(SAT, new DailyForecast(SAT, false, 0, false, 0));

        assertTrue(suggest(chore(true, SAT), days).isEmpty());
    }

    @Test
    public void dismissedOccurrenceStaysHiddenUntilTheNextOne() {
        Map<LocalDate, DailyForecast> days = forecast(day(SAT, 70, 60), day(SUN, 10, 45),
                day(LocalDate.of(2026, 11, 14), 70, 60), day(LocalDate.of(2026, 11, 15), 10, 45));
        Chore dismissed = ChoreSchedule.dismissSuggestion(chore(true, SAT));
        assertTrue(suggest(dismissed, days).isEmpty());

        Chore nextOccurrence = ChoreSchedule.complete(dismissed, SAT, "occ-2");

        assertEquals(1, suggest(nextOccurrence, days).size());
    }

    @Test
    public void movedChoreIsJudgedOnItsNewDate() {
        Chore moved = ChoreSchedule.reschedule(chore(true, SAT), SUN);

        assertTrue(suggest(moved, forecast(day(SAT, 70, 60), day(SUN, 10, 45))).isEmpty());
    }

    @Test
    public void onlyAffectedChoresGetCards() {
        Chore wash = chore(true, SAT);
        Chore clean = new Chore("clean", "욕실 청소", "CLEANING", false, 6, SAT, SAT, "occ-9", 1, null, null, "", "", "alice");

        List<Suggestion> cards = SuggestionEngine.suggest(Arrays.asList(clean, wash),
                forecast(day(SAT, 70, 60), day(SUN, 10, 45)), TODAY);

        assertEquals(1, cards.size());
        assertEquals("wash", cards.get(0).choreId);
    }
}
