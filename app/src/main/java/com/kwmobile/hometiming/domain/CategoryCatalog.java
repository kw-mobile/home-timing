package com.kwmobile.hometiming.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** House-scoped names. Retired identifiers remain redirects, so records and schedules never move. */
public final class CategoryCatalog {
    public static final int MAX_IDENTIFIERS = 1000;
    public final boolean chore;
    public final long revision;
    public final Map<String, String> labels;
    public final Map<String, String> redirects;

    public CategoryCatalog(boolean chore, long revision, Map<String, String> labels, Map<String, String> redirects) {
        if (revision < 1 || labels.isEmpty() || labels.size() + redirects.size() > MAX_IDENTIFIERS)
            throw new IllegalArgumentException("INVALID");
        this.chore = chore;
        this.revision = revision;
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        this.redirects = Collections.unmodifiableMap(new LinkedHashMap<>(redirects));
        Set<String> unique = new HashSet<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (!validId(entry.getKey()) || !validName(entry.getValue()) || !unique.add(entry.getValue()))
                throw new IllegalArgumentException("INVALID");
        }
        for (String id : redirects.keySet()) {
            if (!validId(id) || labels.containsKey(id) || resolve(id) == null)
                throw new IllegalArgumentException("INVALID");
        }
    }

    public static boolean validId(String id) {
        return id != null && id.matches("(default_[a-z_]+|legacy_[0-9a-f]{32}|c_[0-9a-f]{32})");
    }
    public static boolean validName(String name) {
        return name != null && name.equals(name.trim()) && !name.isEmpty()
                && name.codePointCount(0, name.length()) <= 30 && !"전체".equals(name);
    }
    public static CategoryCatalog defaults(boolean chore) {
        Map<String, String> names = new LinkedHashMap<>();
        if (chore) {
            names.put("default_bedding", "세탁");
            names.put("default_dry", "건조");
            names.put("default_ventilation", "환기");
            names.put("default_cleaning", "청소");
        } else {
            names.put("default_bathroom", "욕실");
            names.put("default_kitchen", "주방");
        }
        names.put("default_other", "기타");
        return new CategoryCatalog(chore, 1, names, Collections.emptyMap());
    }

    public static String legacyLabel(boolean chore, String value) {
        if (!chore) return "ETC".equals(value) ? "기타" : value;
        switch (value) {
            case ChoreDraft.TYPE_BEDDING_WASH: return "세탁";
            case ChoreDraft.TYPE_OUTDOOR_DRY: return "건조";
            case ChoreDraft.TYPE_VENTILATION: return "환기";
            case ChoreDraft.TYPE_CLEANING: return "청소";
            default: return "기타";
        }
    }
    public static String legacyId(boolean chore, String value) {
        String name = legacyLabel(chore, value);
        CategoryCatalog defaults = defaults(chore);
        String known = defaults.idForName(name);
        if (known != null) return known;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(((chore ? "chore:" : "item:") + name).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("legacy_");
            for (int i = 0; i < 16; i++) out.append(String.format(Locale.ROOT, "%02x", hash[i] & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public String resolve(String id) {
        Set<String> visited = new HashSet<>();
        while (id != null && !labels.containsKey(id)) {
            if (!visited.add(id)) return null;
            id = redirects.get(id);
        }
        return id;
    }
    public String label(String id, String fallback) {
        String resolved = resolve(id);
        return resolved == null ? fallback : labels.get(resolved);
    }
    public String idForName(String name) {
        for (Map.Entry<String, String> e : labels.entrySet()) if (e.getValue().equals(name)) return e.getKey();
        return null;
    }
    public CategoryCatalog add(String id, String input) {
        String name = input == null ? "" : input.trim();
        if (!validId(id) || !validName(name)) throw new IllegalArgumentException("INVALID");
        if (labels.containsKey(id) || redirects.containsKey(id) || idForName(name) != null)
            throw new IllegalArgumentException("DUPLICATE");
        Map<String, String> next = new LinkedHashMap<>(labels); next.put(id, name);
        return new CategoryCatalog(chore, revision + 1, next, redirects);
    }
    public CategoryCatalog rename(String id, String input) {
        String name = input == null ? "" : input.trim();
        if (!labels.containsKey(id)) throw new IllegalArgumentException("CHANGED");
        if (!validName(name)) throw new IllegalArgumentException("INVALID");
        String duplicate = idForName(name);
        if (duplicate != null && !duplicate.equals(id)) throw new IllegalArgumentException("DUPLICATE");
        Map<String, String> next = new LinkedHashMap<>(labels); next.put(id, name);
        return new CategoryCatalog(chore, revision + 1, next, redirects);
    }
    public CategoryCatalog delete(String id, String target) {
        if (labels.size() <= 1) throw new IllegalArgumentException("LAST");
        if (id.equals(target) || !labels.containsKey(id) || !labels.containsKey(target))
            throw new IllegalArgumentException("CHANGED");
        Map<String, String> next = new LinkedHashMap<>(labels); next.remove(id);
        Map<String, String> links = new LinkedHashMap<>(redirects); links.put(id, target);
        return new CategoryCatalog(chore, revision + 1, next, links);
    }
    public CategoryCatalog alias(String id, String target) {
        if (!id.startsWith("legacy_") || labels.containsKey(id) || redirects.containsKey(id)
                || !labels.containsKey(target)) throw new IllegalArgumentException("CHANGED");
        Map<String, String> links = new LinkedHashMap<>(redirects); links.put(id, target);
        return new CategoryCatalog(chore, revision + 1, labels, links);
    }
}
