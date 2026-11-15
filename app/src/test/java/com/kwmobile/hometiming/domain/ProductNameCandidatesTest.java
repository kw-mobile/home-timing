package com.kwmobile.hometiming.domain;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public final class ProductNameCandidatesTest {
 private ProductNameCandidates.Line line(String s,double h){return new ProductNameCandidates.Line(s,.5,.4,h);}
 @Test public void rejectsPackagingNoiseAndReturnsOnlyObservedName(){
  assertEquals(List.of("새브랜드 칫솔"),ProductNameCandidates.select(List.of(line("고객상담 1234-5678",.2),line("500 ml",.3),line("주의사항",.1),line("새브랜드 칫솔",.08))));
 }
 @Test public void normalizesDeduplicatesAndCapsThree(){
  List<String> names=ProductNameCandidates.select(List.of(line("  샤워  필터 ",.2),line("샤워 필터",.1),line("Brush",.15),line("Soap",.12),line("Towel",.01)));
  assertEquals(List.of("샤워 필터","Brush","Soap"),names);
 }
 @Test public void preservesUnknownWordsAndDoesNotInferCycleOrIdentity(){
  assertEquals(List.of("UNKNOWN ZX-900"),ProductNameCandidates.select(List.of(line("UNKNOWN ZX-900",.1),line("90",.5))));
 }
 @Test public void emptyAndOverlongLinesDoNotBecomeNames(){
  assertTrue(ProductNameCandidates.select(List.of(line("",.1),line("a".repeat(61),.2),line("1234",.2))).isEmpty());
 }
}
