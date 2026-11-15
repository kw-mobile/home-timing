package com.kwmobile.hometiming.domain;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HouseholdNamesTest {
    private HouseholdNames.Observation detection(String label,double score,double center,double area){return new HouseholdNames.Observation(label,score,center,.5,area);}
    @Test public void genericNamesCoverMoreThanPresets(){
        assertEquals(Arrays.asList("가위","컵","칫솔"),HouseholdNames.visual(Arrays.asList(detection("toothbrush",.8,.5,.2),detection("cup",.9,.5,.2),detection("scissors",.95,.5,.3))));
        assertEquals(Arrays.asList("키보드"),HouseholdNames.visual(Arrays.asList(detection("keyboard",.9,.5,.3))));
    }
    @Test public void unknownLowScoresAndInvalidGeometryNeverBecomeNames(){
        assertTrue(HouseholdNames.visual(Arrays.asList(detection("person",.99,.5,.8),detection("???",.9,.5,.4),detection("cup",.44,.5,.4),detection("toothbrush",Double.NaN,.5,.3),detection("bottle",.8,2,.3),detection("book",.8,.5,0))).isEmpty());
    }
    @Test public void duplicateObjectsDoNotFillCandidateSlots(){
        assertEquals(Arrays.asList("칫솔","컵"),HouseholdNames.visual(Arrays.asList(detection("toothbrush",.8,.5,.3),detection("toothbrush",.9,.5,.1),detection("cup",.7,.5,.4))));
    }
    @Test public void packagingProducesGeneralNounsNotBrands(){
        assertEquals(Arrays.asList("칫솔","샤워필터","수세미"),HouseholdNames.text(Arrays.asList("ACME 초극세모 칫솔 3000", "BRAND SHOWER FILTER PLUS", "Premium sponge")));
        assertTrue(HouseholdNames.text(Arrays.asList("ACME MAX3000", "소프트 초극세모", "cupboard" )).isEmpty());
        assertTrue(HouseholdNames.text(Arrays.asList("Apple iPhone Pro")).isEmpty());
        assertEquals(Arrays.asList("칫솔"),HouseholdNames.text(Arrays.asList("ACME Toothbrushes")));
    }
    @Test public void longerProductTermsAndKoreanWhitespaceArePreserved(){
        assertEquals(Arrays.asList("칫솔꽂이","베개커버","칫솔"),HouseholdNames.text(Arrays.asList("브랜드 칫솔 꽂이", "베개 커버", "전동 칫솔")));
    }
    @Test public void visualAndTextResultsDeduplicateAndStayBounded(){
        assertEquals(Arrays.asList("칫솔","컵","샤워필터"),HouseholdNames.combine(Arrays.asList("칫솔","컵"),Arrays.asList("칫솔","샤워필터","수세미")));
    }
}
