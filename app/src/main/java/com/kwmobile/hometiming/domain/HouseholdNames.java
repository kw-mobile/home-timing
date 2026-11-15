package com.kwmobile.hometiming.domain;

import java.util.*;
import java.util.regex.Pattern;

/** General nouns only. Does not identify brands, SKUs or replacement cycles. */
public final class HouseholdNames {
    private HouseholdNames() { }
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        String[] pairs = {
            "bicycle|자전거", "car|자동차", "motorcycle|오토바이", "airplane|비행기", "bus|버스", "train|기차", "truck|트럭", "boat|보트",
            "traffic light|신호등", "fire hydrant|소화전", "stop sign|정지 표지판", "parking meter|주차 미터기", "bench|벤치",
            "bird|새", "cat|고양이", "dog|강아지", "horse|말", "sheep|양", "cow|소", "elephant|코끼리", "bear|곰", "zebra|얼룩말", "giraffe|기린",
            "backpack|배낭", "umbrella|우산", "handbag|가방", "tie|넥타이", "suitcase|여행 가방", "frisbee|원반", "skis|스키", "snowboard|스노보드",
            "sports ball|공", "kite|연", "baseball bat|야구 배트", "baseball glove|야구 글러브", "skateboard|스케이트보드", "surfboard|서핑 보드", "tennis racket|테니스 라켓",
            "bottle|병", "wine glass|와인잔", "cup|컵", "fork|포크", "knife|칼", "spoon|숟가락", "bowl|그릇",
            "banana|바나나", "apple|사과", "sandwich|샌드위치", "orange|오렌지", "broccoli|브로콜리", "carrot|당근", "hot dog|핫도그", "pizza|피자", "donut|도넛", "cake|케이크",
            "chair|의자", "couch|소파", "potted plant|화분", "bed|침대", "dining table|식탁", "toilet|변기", "tv|TV", "laptop|노트북", "mouse|마우스", "remote|리모컨", "keyboard|키보드", "cell phone|휴대폰",
            "microwave|전자레인지", "oven|오븐", "toaster|토스터", "sink|세면대", "refrigerator|냉장고", "book|책", "clock|시계", "vase|꽃병", "scissors|가위", "teddy bear|인형", "hair drier|헤어드라이어", "toothbrush|칫솔"
        };
        for (String pair : pairs) { String[] p = pair.split("\\|"); NAMES.put(p[0], p[1]); }
    }
    public static final class Observation {
        public final String label;
        public final double confidence, centerX, centerY, area;
        public Observation(String label, double confidence, double centerX, double centerY, double area) {
            this.label=label; this.confidence=confidence; this.centerX=centerX; this.centerY=centerY; this.area=area;
        }
    }
    public static List<String> visual(List<Observation> observations) {
        Map<String,Double> scores = new HashMap<>();
        for (Observation o : observations) {
            String name = NAMES.get(o.label);
            if (name == null || !Double.isFinite(o.confidence) || o.confidence < .45 || o.confidence > 1
                    || !Double.isFinite(o.area) || o.area < .0001 || o.area > 1
                    || !Double.isFinite(o.centerX) || !Double.isFinite(o.centerY)
                    || o.centerX < 0 || o.centerX > 1 || o.centerY < 0 || o.centerY > 1) continue;
            double score=o.confidence - .1*(Math.abs(o.centerX-.5)+Math.abs(o.centerY-.5));
            scores.merge(name,score,Math::max);
        }
        List<String> names = new ArrayList<>(scores.keySet());
        names.sort(Comparator.<String>comparingDouble(scores::get).reversed().thenComparing(s -> s));
        return Collections.unmodifiableList(new ArrayList<>(names.subList(0,Math.min(3,names.size()))));
    }
    public static List<String> text(List<String> lines) {
        // Longer Korean terms win so a toothbrush holder is not reduced to a toothbrush.
        List<String[]> terms=new ArrayList<>();
        Set<String> textClasses=new HashSet<>(Arrays.asList("backpack","umbrella","handbag","tie","suitcase","bottle","wine glass","cup","fork","knife","spoon","bowl","chair","couch","potted plant","bed","dining table","toilet","tv","laptop","mouse","remote","keyboard","cell phone","microwave","oven","toaster","sink","refrigerator","book","clock","vase","scissors","teddy bear","hair drier","toothbrush"));
        for (Map.Entry<String,String> e:NAMES.entrySet()) if(textClasses.contains(e.getKey()))terms.add(new String[]{e.getValue(),e.getKey(),e.getValue()});
        String[] extra={"샤워필터|샤워필터", "베개커버|베개커버", "칫솔꽂이|칫솔꽂이", "전동칫솔|칫솔", "수세미|수세미", "행주|행주", "수건|수건", "치약|치약", "세제|세제", "비누|비누", "샴푸|샴푸", "린스|린스", "면도기|면도기", "면도날|면도날", "샤워기|샤워기", "필터|필터", "마스크|마스크", "건전지|건전지", "휴지|휴지", "양말|양말", "장갑|장갑", "물티슈|물티슈", "옷걸이|옷걸이", "도마|도마", "냄비|냄비", "프라이팬|프라이팬", "청소기|청소기", "청소솔|청소솔"};
        for(String pair:extra){String[] p=pair.split("\\|"); terms.add(new String[]{p[0],"",p[1]});}
        terms.add(new String[]{"샤워필터","shower filter","샤워필터"});
        terms.add(new String[]{"베개커버","pillowcase","베개커버"});
        terms.add(new String[]{"수세미","sponge","수세미"});
        terms.add(new String[]{"칫솔","tooth brush","칫솔"});
        terms.add(new String[]{"휴대폰","smartphone","휴대폰"});
        terms.sort(Comparator.<String[]>comparingInt(t->t[0].length()).reversed());
        Set<String> found=new LinkedHashSet<>();
        for(String line:lines){
            String korean=ProductNameCandidates.normalize(line).replace(" ","");
            String english=ProductNameCandidates.normalize(line).toLowerCase(Locale.ROOT);
            for(String[] term:terms){
                boolean koreanMatch=term[0].matches("[가-힣]{2,}") ? korean.contains(term[0])
                        : Pattern.compile("(?<![\\p{L}])"+Pattern.quote(term[0])+"(?![\\p{L}])").matcher(korean).find();
                if(koreanMatch || !term[1].isEmpty() && Pattern.compile("(?<![a-z])"+Pattern.quote(term[1])+"(?:es|s)?(?![a-z])").matcher(english).find()){
                    found.add(term[2]); break;
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(found));
    }
    public static List<String> combine(List<String> visual, List<String> text) {
        Set<String> combined=new LinkedHashSet<>(visual); combined.addAll(text);
        List<String> names=new ArrayList<>(combined);
        return Collections.unmodifiableList(new ArrayList<>(names.subList(0,Math.min(3,names.size()))));
    }
}
