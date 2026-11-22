package com.kwmobile.hometiming.ui.main;

import android.os.Bundle;

import androidx.lifecycle.ViewModel;

import com.kwmobile.hometiming.data.preview.PreviewStore;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Navigation, form drafts and fixture state. Contains no View or Activity references. */
public final class DesignPreviewViewModel extends ViewModel {
    // In-memory request binding intentionally does not survive process death or account/house changes.
    Draft photoDraft;
    String photoRequest;
    com.kwmobile.hometiming.data.auth.OperationScope.Token photoScope;

    public static final class Route implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String screen, id;

        public Route(String screen) {
            this(screen, "");
        }

        public Route(String screen, String id) {
            this.screen = screen;
            this.id = id;
        }

        public String key() {
            return screen + ":" + id;
        }
    }

    public static final class Draft implements Serializable {
        private static final long serialVersionUID = 1L;
        public String id = "",
                name = "",
                location = "",
                productName = "",
                memo = "",
                category = "기타",
                cycle = "60";
        public LocalDate date = PreviewStore.today();
        public int weekday = date.getDayOfWeek().getValue();
        public int itemPresetScrollX;
        public boolean chore = false,
                weather = false,
                dirty = false,
                optionalExpanded = false,
                cycleEdited = false;
    }

    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        public PreviewStore store = new PreviewStore();
        public boolean live;
        public Route route = new Route("home");
        public ArrayDeque<Route> back = new ArrayDeque<>();
        public Map<String, Integer> scrolls = new HashMap<>();
        public Map<String, String> authFields = new HashMap<>();
        public LinkedHashSet<String> itemCategories = new LinkedHashSet<>();
        public LinkedHashSet<String> choreCategories = new LinkedHashSet<>();
        public LinkedHashSet<String> removedItemCategories = new LinkedHashSet<>();
        public LinkedHashSet<String> removedChoreCategories = new LinkedHashSet<>();
        public Draft draft;
        public Draft componentDraft = new Draft();
        public Map<String, LocalDate> completionDates = new HashMap<>();
        public String filter = "전체", weather = "auto", condition = "clear", scenario = "normal";
        public String region = "서울 마포구",
                person = "나",
                time = "09:00",
                invite = "284619",
                joinCode = "";
        public int members = 2;
        public LocalDate selected = PreviewStore.today();
        public YearMonth month = YearMonth.from(selected);
        public boolean expanded = false,
                sheetHidden = false,
                owner = true,
                joined = false,
                notifications = false,
                permissionGranted = false;
        public boolean passwordVisible = false, initialized = false;
        public int advanceDays = 3;
    }

    public State state = new State();

    @SuppressWarnings("deprecation")
    public void restore(Bundle saved) {
        if (!state.initialized && saved != null) {
            Serializable value = saved.getSerializable("preview_state");
            if (value instanceof State) state = (State) value;
        }
        if (!state.live && state.members == 0) state.members = 2;
        if (state.joinCode == null) state.joinCode = "";
        if (state.completionDates == null) state.completionDates = new HashMap<>();
        if (state.componentDraft == null) state.componentDraft = new Draft();
        if (state.itemCategories == null) state.itemCategories = new LinkedHashSet<>();
        if (state.choreCategories == null) state.choreCategories = new LinkedHashSet<>();
        state.initialized = true;
    }

    public List<String> categories(boolean chore, String[] defaults) {
        if (state.live) return new ArrayList<>(chore ? state.choreCategories : state.itemCategories);
        LinkedHashSet<String> result = new LinkedHashSet<>(java.util.Arrays.asList(defaults));
        result.removeAll(removedCategories(chore));
        result.addAll(chore ? state.choreCategories : state.itemCategories);
        for (PreviewStore.Item item : state.store.items) {
            if (item.chore == chore && item.category != null && !item.category.trim().isEmpty())
                result.add(item.category.trim());
        }
        return new ArrayList<>(result);
    }

    public boolean addCategory(boolean chore, String input, String[] defaults, String allLabel) {
        String name = input == null ? "" : input.trim();
        if (name.isEmpty()
                || name.codePointCount(0, name.length())
                        > com.kwmobile.hometiming.domain.ItemInputValidator.CATEGORY_MAX_LENGTH
                || name.equals(allLabel)
                || categories(chore, defaults).contains(name)) return false;
        return (chore ? state.choreCategories : state.itemCategories).add(name);
    }

    public boolean applyItemPreset(String name, String category, int days, String[] defaults) {
        Draft draft = state.draft;
        if (draft == null || draft.chore || name == null || name.trim().isEmpty()
                || name.trim().length() > 60 || days < 1 || days > 730) return false;
        List<String> names = categories(false, defaults);
        draft.name = name.trim();
        draft.category = names.contains(category) ? category
                : defaultCategory(false, defaults, draft.category);
        draft.cycle = String.valueOf(days);
        draft.cycleEdited = true;
        draft.dirty = true;
        return true;
    }

    private LinkedHashSet<String> removedCategories(boolean chore) {
        // Older serialized preview states do not contain these fields.
        if (state.removedItemCategories == null) state.removedItemCategories = new LinkedHashSet<>();
        if (state.removedChoreCategories == null) state.removedChoreCategories = new LinkedHashSet<>();
        return chore ? state.removedChoreCategories : state.removedItemCategories;
    }

    public String defaultCategory(boolean chore, String[] defaults, String preferred) {
        List<String> names = categories(chore, defaults);
        return names.contains(preferred) ? preferred : names.isEmpty() ? preferred : names.get(0);
    }

    public boolean renameCategory(
            boolean chore, String oldName, String input, String[] defaults, String allLabel) {
        String name = input == null ? "" : input.trim();
        List<String> names = categories(chore, defaults);
        if (!names.contains(oldName) || name.isEmpty() || name.equals(allLabel)
                || name.codePointCount(0, name.length())
                        > com.kwmobile.hometiming.domain.ItemInputValidator.CATEGORY_MAX_LENGTH
                || (!name.equals(oldName) && names.contains(name))) return false;
        if (name.equals(oldName)) return true;
        removedCategories(chore).add(oldName);
        LinkedHashSet<String> custom = chore ? state.choreCategories : state.itemCategories;
        custom.remove(oldName);
        custom.add(name);
        moveCategoryReferences(chore, oldName, name);
        if (!chore && oldName.equals(state.filter)) state.filter = name;
        return true;
    }

    public boolean categoryInUse(boolean chore, String name) {
        for (PreviewStore.Item item : state.store.items) {
            if (item.chore == chore && item.category != null
                    && name.equals(item.category.trim())) return true;
        }
        return draftUsesCategory(state.draft, chore, name)
                || draftUsesCategory(state.componentDraft, chore, name);
    }

    private boolean draftUsesCategory(Draft draft, boolean chore, String name) {
        return draft != null && draft.chore == chore && draft.category != null
                && name.equals(draft.category.trim());
    }

    public boolean deleteCategory(
            boolean chore, String name, String replacement, String[] defaults, String allLabel) {
        List<String> names = categories(chore, defaults);
        if (!names.contains(name) || names.size() <= 1 || name.equals(replacement)) return false;
        if (categoryInUse(chore, name) && !names.contains(replacement)) return false;
        removedCategories(chore).add(name);
        (chore ? state.choreCategories : state.itemCategories).remove(name);
        moveCategoryReferences(chore, name, replacement);
        if (!chore && name.equals(state.filter)) state.filter = allLabel;
        return true;
    }

    private void moveCategoryReferences(boolean chore, String name, String replacement) {
        for (PreviewStore.Item item : state.store.items) {
            if (item.chore == chore && item.category != null && name.equals(item.category.trim())) {
                item.category = replacement;
                item.revision++;
            }
        }
        if (draftUsesCategory(state.draft, chore, name)) {
            state.draft.category = replacement;
            state.draft.dirty = true;
        }
        if (draftUsesCategory(state.componentDraft, chore, name)) {
            state.componentDraft.category = replacement;
            state.componentDraft.dirty = true;
        }
    }

    public void save(Bundle out) {
        // ViewModels retain in-memory drafts during rotation. Live credentials and cached remote
        // presentation are never persisted to the OS saved-state Bundle.
        if (!state.live) out.putSerializable("preview_state", state);
    }

    public void navigate(String screen, String id) {
        state.back.push(state.route);
        state.route = new Route(screen, id);
    }

    public void tab(String screen) {
        state.back.clear();
        state.route = new Route(screen);
    }

    public boolean back() {
        if (!state.back.isEmpty()) {
            state.route = state.back.pop();
            return true;
        }
        if (!state.route.screen.equals("home")) {
            state.route = new Route("home");
            return true;
        }
        return false;
    }

    public void edit(String id) {
        Draft d = new Draft();
        PreviewStore.Item item = state.store.find(id);
        if (item != null) {
            d.id = item.id;
            d.name = item.name;
            d.location = item.location;
            d.category = item.category;
            d.productName = item.productName == null ? "" : item.productName;
            d.memo = item.memo == null ? "" : item.memo;
            d.cycleEdited = true;
            d.weekday = item.weekday;
            d.chore = item.chore;
            d.weather = item.weatherSensitive;
            d.cycle = String.valueOf(item.cycle);
            d.date = item.serverItem == null ? item.start : item.serverItem.startDate;
        }
        state.draft = d;
        state.scrolls.remove(new Route("editor", id).key());
        navigate("editor", id);
    }
}
