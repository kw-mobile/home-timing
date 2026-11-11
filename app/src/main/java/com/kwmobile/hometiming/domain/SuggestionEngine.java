package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds weather cards from chores and the summarized forecast (PRD 6-2, F-09). Only
 * weather-sensitive chores whose current date is inside the forecast range get a card, and a card
 * the user dismissed stays hidden for that occurrence only.
 */
public final class SuggestionEngine {
    private SuggestionEngine() {
    }

    public static List<Suggestion> suggest(List<Chore> chores, Map<LocalDate, DailyForecast> forecast, LocalDate today) {
        Objects.requireNonNull(chores, "chores");
        Objects.requireNonNull(forecast, "forecast");
        Objects.requireNonNull(today, "today");
        List<Suggestion> suggestions = new ArrayList<>();
        for (Chore chore : chores) {
            Suggestion suggestion = suggestFor(chore, forecast, today);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return Collections.unmodifiableList(suggestions);
    }

    private static Suggestion suggestFor(Chore chore, Map<LocalDate, DailyForecast> forecast, LocalDate today) {
        if (!chore.weatherSensitive || chore.isSuggestionDismissed()) {
            return null;
        }
        DailyForecast due = forecast.get(chore.nextDueDate);
        switch (LaundryAdvisor.judge(due)) {
            case CAUTION:
                return new Suggestion(Suggestion.Kind.CAUTION, chore, due, null, null);
            case BAD: {
                LocalDate target = LaundryAdvisor.findAlternative(chore.nextDueDate, forecast, today);
                return target == null
                        ? new Suggestion(Suggestion.Kind.INDOOR, chore, due, null, null)
                        : new Suggestion(Suggestion.Kind.MOVE, chore, due, target, forecast.get(target));
            }
            case GOOD:
            case UNKNOWN:
            default:
                return null;
        }
    }
}
