package com.kwmobile.hometiming.domain;

import java.time.LocalDate;

/**
 * What the register/edit form submits. Text is trimmed; empty optional text means "not set".
 * Validate with {@link ItemInputValidator#validateDraft} before saving.
 */
public final class ItemDraft {
    /** Default category when the user does not pick one (docs/MVP.md: 기본 분류 기타). */
    public static final String DEFAULT_CATEGORY = "ETC";

    public final String name;
    public final String category;
    public final LocalDate startDate;
    public final Integer cycleDays;
    public final String productName;
    public final String location;
    public final String memo;

    public ItemDraft(String name, String category, LocalDate startDate, Integer cycleDays,
                     String productName, String location, String memo) {
        this.name = trim(name);
        String picked = trim(category);
        this.category = picked.isEmpty() ? DEFAULT_CATEGORY : picked;
        this.startDate = startDate;
        this.cycleDays = cycleDays;
        this.productName = trim(productName);
        this.location = trim(location);
        this.memo = trim(memo);
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
