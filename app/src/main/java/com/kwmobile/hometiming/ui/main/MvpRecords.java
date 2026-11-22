package com.kwmobile.hometiming.ui.main;

import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.domain.Chore;
import com.kwmobile.hometiming.domain.CategoryCatalog;
import com.kwmobile.hometiming.domain.ChoreDraft;
import com.kwmobile.hometiming.domain.Item;
import java.time.LocalDate;

/** Converts immutable server records to the existing screen presentation, retaining write snapshots. */
final class MvpRecords {
    private MvpRecords() { }

    static PreviewStore.Item item(Item item) { return item(item, null); }

    static PreviewStore.Item item(Item item, CategoryCatalog catalog) {
        PreviewStore.Item row = new PreviewStore.Item("item:" + item.id, item.name,
                catalog == null ? CategoryCatalog.legacyLabel(false, item.category)
                        : catalog.label(item.categoryId.isEmpty() ? CategoryCatalog.legacyId(false, item.category)
                        : item.categoryId, CategoryCatalog.legacyLabel(false, item.category)), item.location, false,
                item.cycleDays, item.lastReplacedDate, item.nextDueDate);
        row.serverItem = item;
        row.productName = item.productName;
        row.memo = item.memo;
        row.revision = item.revision;
        row.createdBy = item.createdBy;
        row.completedOn = item.hasCompleted ? item.lastReplacedDate : null;
        return row;
    }

    static PreviewStore.Item chore(Chore chore, LocalDate today) { return chore(chore, today, null); }

    static PreviewStore.Item chore(Chore chore, LocalDate today, CategoryCatalog catalog) {
        LocalDate start = chore.lastDoneDate == null ? today : chore.lastDoneDate;
        PreviewStore.Item row = new PreviewStore.Item("chore:" + chore.id, chore.name,
                catalog == null ? (chore.category.isEmpty() ? category(chore.type) : chore.category)
                        : catalog.label(chore.categoryId.isEmpty() ? CategoryCatalog.legacyId(true, chore.type)
                        : chore.categoryId, chore.category.isEmpty() ? category(chore.type) : chore.category),
                chore.location, true, 7, start, chore.nextDueDate);
        row.serverChore = chore;
        row.weekday = chore.weekday;
        row.weatherSensitive = chore.weatherSensitive;
        row.plannedDue = chore.plannedDueDate;
        row.proposalDismissed = chore.isSuggestionDismissed();
        row.memo = chore.memo;
        row.revision = chore.revision;
        row.createdBy = chore.createdBy;
        row.completedOn = chore.lastDoneDate;
        return row;
    }

    static String category(String type) {
        switch (type) {
            case ChoreDraft.TYPE_BEDDING_WASH: return "세탁";
            case ChoreDraft.TYPE_OUTDOOR_DRY: return "건조";
            case ChoreDraft.TYPE_VENTILATION: return "환기";
            case ChoreDraft.TYPE_CLEANING: return "청소";
            default: return "기타";
        }
    }

    static String choreType(String category) {
        switch (category) {
            case "세탁": return ChoreDraft.TYPE_BEDDING_WASH;
            case "건조": return ChoreDraft.TYPE_OUTDOOR_DRY;
            case "환기": return ChoreDraft.TYPE_VENTILATION;
            case "청소": return ChoreDraft.TYPE_CLEANING;
            default: return ChoreDraft.TYPE_ETC;
        }
    }
}
