package com.kwmobile.hometiming.domain;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
public final class CalendarEntryTest {
 @Test public void allDayUtcIncludesLeapDayAndExclusiveNextDay() {
  CalendarEntry e=new CalendarEntry("k","n",LocalDate.of(2028,2,29),false);
  assertEquals(Instant.parse("2028-02-29T00:00:00Z").toEpochMilli(),e.startMillis());
  assertEquals(Instant.parse("2028-03-01T00:00:00Z").toEpochMilli(),e.endMillis());
 }
 @Test public void sameDocumentIdAcrossKindsDoesNotCollideAndOnlyRealCompletionExports() {
  LocalDate date=LocalDate.of(2026,11,15);
  Item item=new Item("same","brush","bath",date,30,date,date.plusDays(30),"one",1,"","","","u",false);
  Chore chore=new Chore("same","wash","LAUNDRY",true,7,date,date.plusDays(1),"c",2,null,date.minusDays(7),"","","u");
  List<CalendarEntry> rows=CalendarEntry.project(List.of(item),List.of(chore));
  assertEquals(3,rows.size()); assertEquals(3,rows.stream().map(r->r.key).distinct().count());
  assertEquals(date.plusDays(1),rows.get(1).date); assertEquals(date.minusDays(7),rows.get(2).date);
  assertTrue(rows.get(2).completed);
 }
 @Test public void completionAndScheduleChangesKeepStableProjectionKeys() {
  LocalDate date=LocalDate.of(2026,11,15);
  Item item=new Item("i","brush","bath",date,30,date,date.plusDays(30),"one",1,"","","","u",false);
  Item done=ItemSchedule.complete(item,date,"two");
  List<CalendarEntry> before=CalendarEntry.project(List.of(item),List.of());
  List<CalendarEntry> after=CalendarEntry.project(List.of(done),List.of());
  assertEquals(before.get(0).key,after.get(0).key); assertEquals(2,after.size());
  assertTrue(after.get(1).completed); assertEquals(date,after.get(1).date);
 }
}
