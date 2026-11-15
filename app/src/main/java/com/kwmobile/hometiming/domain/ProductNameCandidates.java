package com.kwmobile.hometiming.domain;

import java.text.Normalizer;
import java.util.*;

/** Ranks observed packaging text, not product identities. Never invents brands, models or replacement cycles. */
public final class ProductNameCandidates {
    private ProductNameCandidates() { }
    public static final class Line {
        public final String text;
        public final double centerX, centerY, height;
        public Line(String text, double centerX, double centerY, double height) {
            this.text = text; this.centerX = centerX; this.centerY = centerY; this.height = height;
        }
    }
    public static List<String> select(List<Line> lines) {
        Map<String, Double> scores = new HashMap<>();
        for (Line line : lines) {
            String text = normalize(line.text);
            if (text.length() < 2 || text.length() > 60 || !text.matches(".*[\\p{L}].*")) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.matches(".*(고객|상담|전화|주소|성분|제조원|판매원|유통기한|사용기한|주의사항|customer|ingredients|www\\.|https?://|made in).*")) continue;
            if (text.matches("[0-9.,\\s]+(?i:ml|l|g|kg|mg|cm|mm|매|개|입|장|회|%)")) continue;
            double score = Math.min(.25, Math.max(0, line.height)) * 8
                    - Math.abs(line.centerX - .5) - Math.abs(line.centerY - .4) * .5
                    - Math.max(0, text.length() - 24) * .02;
            scores.merge(text, score, Math::max);
        }
        List<String> result = new ArrayList<>(scores.keySet());
        result.sort(Comparator.<String>comparingDouble(scores::get).reversed().thenComparing(s -> s));
        return Collections.unmodifiableList(new ArrayList<>(result.subList(0, Math.min(3, result.size()))));
    }
    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
    }
}
