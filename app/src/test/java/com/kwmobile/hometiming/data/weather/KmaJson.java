package com.kwmobile.hometiming.data.weather;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;

/** Builds short-range forecast responses in the documented shape. */
final class KmaJson {
    private final JsonArray items = new JsonArray();
    private final String baseDate;
    private final String baseTime;
    private final int nx;
    private final int ny;
    private Integer totalCount;

    KmaJson(BaseTime base, int nx, int ny) {
        this.baseDate = base.baseDate();
        this.baseTime = base.baseTime();
        this.nx = nx;
        this.ny = ny;
    }

    KmaJson item(String date, String time, String category, String value) {
        JsonObject item = new JsonObject();
        item.addProperty("baseDate", baseDate);
        item.addProperty("baseTime", baseTime);
        item.addProperty("category", category);
        item.addProperty("fcstDate", date);
        item.addProperty("fcstTime", time);
        if (value != null) {
            item.addProperty("fcstValue", value);
        }
        item.addProperty("nx", nx);
        item.addProperty("ny", ny);
        items.add(item);
        return this;
    }

    /** POP, PTY and REH for one hour. */
    KmaJson hour(String date, String time, String pop, String pty, String reh) {
        return item(date, time, "POP", pop).item(date, time, "PTY", pty).item(date, time, "REH", reh);
    }

    KmaJson claimTotal(int total) {
        totalCount = total;
        return this;
    }

    String build() {
        JsonObject itemsNode = new JsonObject();
        itemsNode.add("item", items);
        JsonObject body = new JsonObject();
        body.addProperty("dataType", "JSON");
        body.add("items", itemsNode);
        body.addProperty("pageNo", 1);
        body.addProperty("numOfRows", 2000);
        body.addProperty("totalCount", totalCount == null ? items.size() : totalCount);
        return wrap("00", body);
    }

    static String error(String code) {
        return wrap(code, null);
    }

    private static String wrap(String code, JsonObject body) {
        JsonObject header = new JsonObject();
        header.addProperty("resultCode", code);
        header.addProperty("resultMsg", "MSG");
        JsonObject response = new JsonObject();
        response.add("header", header);
        if (body != null) {
            response.add("body", body);
        }
        JsonObject root = new JsonObject();
        root.add("response", response);
        return root.toString();
    }
}
