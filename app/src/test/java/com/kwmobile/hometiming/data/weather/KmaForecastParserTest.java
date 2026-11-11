package com.kwmobile.hometiming.data.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.kwmobile.hometiming.domain.BaseTimeCalculator;
import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;
import com.kwmobile.hometiming.domain.DailyForecast;
import com.kwmobile.hometiming.domain.ForecastHour;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.Test;

public class KmaForecastParserTest {
    private static final BaseTime BASE = BaseTimeCalculator.latest(LocalDateTime.of(2026, 11, 11, 14, 10));
    private static final BaseTime OTHER_RUN = BaseTimeCalculator.latest(LocalDateTime.of(2026, 11, 11, 17, 10));
    private static final String DAY = "20261112";

    private static KmaJson json() {
        return new KmaJson(BASE, 60, 127);
    }

    private static List<ForecastHour> parse(String body) throws WeatherException {
        return KmaForecastParser.parse(body, BASE, 60, 127);
    }

    private static void assertRefused(WeatherError expected, String body) {
        WeatherException e = assertThrows(WeatherException.class, () -> parse(body));
        assertEquals(expected, e.error);
    }

    @Test
    public void readsTheNeededCategoriesAndIgnoresTheRest() throws WeatherException {
        List<ForecastHour> hours = parse(json()
                .hour(DAY, "0900", "30", "0", "55")
                .item(DAY, "0900", "TMP", "12")
                .item(DAY, "0900", "PCP", "강수없음")
                .hour(DAY, "1000", "60", "1", "80")
                .build());

        assertEquals(2, hours.size());
        ForecastHour first = hours.get(0);
        assertEquals(LocalDate.of(2026, 11, 12), first.date);
        assertEquals(9, first.hour);
        assertEquals(Integer.valueOf(30), first.pop);
        assertEquals(Integer.valueOf(0), first.pty);
        assertEquals(Integer.valueOf(55), first.reh);
        assertEquals(Integer.valueOf(1), hours.get(1).pty);
    }

    @Test
    public void missingOrImpossibleValuesStayNull() throws WeatherException {
        List<ForecastHour> hours = parse(json()
                .hour(DAY, "0900", null, "0", "55")
                .hour(DAY, "1000", "-999", "0", "55")
                .hour(DAY, "1100", "101", "0", "55")
                .hour(DAY, "1200", "30", "8", "55")
                .hour(DAY, "1300", "30", "0", "습함")
                .build());

        assertNull(hours.get(0).pop);
        assertNull(hours.get(1).pop);
        assertNull(hours.get(2).pop);
        assertNull(hours.get(3).pty);
        assertNull(hours.get(4).reh);
    }

    @Test
    public void anHourMissingOneCategoryMakesTheDayUnknown() throws WeatherException {
        KmaJson json = json();
        for (int hour = 9; hour <= 18; hour++) {
            String time = String.format("%02d00", hour);
            json.item(DAY, time, "POP", "10").item(DAY, time, "REH", "50");
            if (hour != 13) {
                json.item(DAY, time, "PTY", "0");
            }
        }

        DailyForecast day = DailyForecast.summarize(parse(json.build()), LocalDate.of(2026, 11, 11), 14)
                .get(LocalDate.of(2026, 11, 12));

        assertFalse(day.complete);
    }

    @Test
    public void refusesAnotherRunOrGrid() {
        assertRefused(WeatherError.INVALID_RESPONSE, new KmaJson(OTHER_RUN, 60, 127).hour(DAY, "0900", "1", "0", "1").build());
        assertRefused(WeatherError.INVALID_RESPONSE, new KmaJson(BASE, 61, 127).hour(DAY, "0900", "1", "0", "1").build());
        assertRefused(WeatherError.INVALID_RESPONSE, new KmaJson(BASE, 60, 128).hour(DAY, "0900", "1", "0", "1").build());
    }

    @Test
    public void refusesATruncatedResponse() {
        assertRefused(WeatherError.INVALID_RESPONSE, json().hour(DAY, "0900", "1", "0", "1").claimTotal(4).build());
    }

    @Test
    public void refusesBadDatesAndTimes() {
        assertRefused(WeatherError.INVALID_RESPONSE, json().hour("20261345", "0900", "1", "0", "1").build());
        assertRefused(WeatherError.INVALID_RESPONSE, json().hour(DAY, "2400", "1", "0", "1").build());
        assertRefused(WeatherError.INVALID_RESPONSE, json().hour(DAY, "0930", "1", "0", "1").build());
        assertRefused(WeatherError.INVALID_RESPONSE, json().hour(DAY, "9", "1", "0", "1").build());
    }

    @Test
    public void refusesDuplicates() {
        assertRefused(WeatherError.INVALID_RESPONSE,
                json().hour(DAY, "0900", "1", "0", "1").item(DAY, "0900", "POP", "90").build());
    }

    @Test
    public void refusesAResponseWithoutARequiredCategory() {
        assertRefused(WeatherError.INVALID_RESPONSE,
                json().item(DAY, "0900", "POP", "1").item(DAY, "0900", "PTY", "0").build());
    }

    @Test
    public void refusesWhatIsNotAForecast() {
        assertRefused(WeatherError.INVALID_RESPONSE, "<OpenAPI_ServiceResponse></OpenAPI_ServiceResponse>");
        assertRefused(WeatherError.INVALID_RESPONSE, "{}");
        assertRefused(WeatherError.INVALID_RESPONSE, "");
    }

    @Test
    public void mapsResultCodes() {
        assertRefused(WeatherError.NO_DATA, KmaJson.error("03"));
        assertRefused(WeatherError.NOT_AUTHORIZED, KmaJson.error("30"));
        assertRefused(WeatherError.SERVER, KmaJson.error("99"));
    }

    @Test
    public void emptyResultIsNoData() {
        assertRefused(WeatherError.NO_DATA, json().build());
    }

    @Test
    public void mapsTheHubErrorShape() {
        // The body API허브 returned for an API that was not applied for.
        assertRefused(WeatherError.NOT_AUTHORIZED, "{\"result\":{\"status\":403,\"message\":\"활용신청이 필요한 API 입니다.\"}}");
        assertRefused(WeatherError.SERVER, "{\"result\":{\"status\":500,\"message\":\"x\"}}");
    }
}
