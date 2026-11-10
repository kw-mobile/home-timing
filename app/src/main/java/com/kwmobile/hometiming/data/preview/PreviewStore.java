package com.kwmobile.hometiming.data.preview;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** In-memory design fixtures only. Never reads or writes a real household. */
public final class PreviewStore implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final class History implements Serializable {
        private static final long serialVersionUID = 1L;
        public final LocalDate date;
        public final String person;
        public History(LocalDate date, String person) { this.date = date; this.person = person; }
    }
    public static final class Item implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String id;
        public String name, category, location;
        public String productName="", memo="";
        public LocalDate completedOn;
        public int revision;
        public String createdBy="나";
        public boolean chore, weatherSensitive, proposalDismissed;
        public int cycle, weekday;
        public LocalDate start, due, plannedDue;
        public final List<History> history = new ArrayList<>();
        public Item(String id, String name, String category, String location, boolean chore,
                    int cycle, LocalDate start, LocalDate due) {
            this.id=id; this.name=name; this.category=category; this.location=location;
            this.chore=chore; this.cycle=cycle; this.start=start; this.due=due;this.plannedDue=due;
            weatherSensitive=chore;weekday=due.getDayOfWeek().getValue();
        }
    }
    public final List<Item> items = new ArrayList<>();
    public static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }
    public PreviewStore() { reset(); }
    public void reset() {
        items.clear(); LocalDate today=today();
        Item brush=new Item("brush", "칫솔", "욕실", "욕실", false, 90,
                today.minusDays(92), today.minusDays(2));
        brush.history.add(new History(today.minusDays(92), "나"));
        brush.history.add(new History(today.minusDays(182), "가족"));
        items.add(brush);
        Item filter=new Item("filter", "샤워기 필터", "욕실", "욕실", false, 60,
                today.minusDays(60), today);
        filter.createdBy="가족";filter.history.add(new History(today.minusDays(60), "가족")); items.add(filter);
        Item bedding=new Item("bedding", "침구 세탁", "세탁", "침실", true, 7,
                today.minusDays(5), today.plusDays(2));
        bedding.history.add(new History(today.minusDays(5), "나")); items.add(bedding);
        items.add(new Item("sponge", "주방 수세미", "주방", "주방", false, 30,
                today.minusDays(20), today.plusDays(10)));
        items.add(new Item("clean", "욕실 청소", "청소", "욕실", true, 7,
                today.minusDays(3), today.plusDays(4)));
    }
    public Item find(String id) {
        for(Item item: items) if(item.id.equals(id)) {if(item.weekday==0)item.weekday=item.due.getDayOfWeek().getValue();if(item.plannedDue==null)item.plannedDue=item.due;return item;}
        return null;
    }
    public List<Item> sorted() {
        List<Item> result=new ArrayList<>(items);
        result.sort(Comparator.comparing(i -> i.due)); return result;
    }
    public Item save(String id, String name, String category, String location, boolean chore,
                     int cycle, LocalDate start, boolean weatherSensitive, int weekday) {
        Item item=find(id);
        boolean keepSchedule=keepsWeeklySchedule(item,chore,start,weekday);
        LocalDate preview=previewDue(id,chore,cycle,start,weekday);
        boolean keepProposal=keepSchedule&&item.weatherSensitive==weatherSensitive;
        if(item==null) {item=new Item(UUID.randomUUID().toString(),name,category,location,chore,cycle,start,start.plusDays(cycle));items.add(item);}
        else {
            item.name=name;item.category=category;item.location=location;item.chore=chore;
            item.cycle=cycle;item.start=start;
        }
        item.weatherSensitive=weatherSensitive;item.weekday=weekday;item.revision++;
        if(!keepProposal)item.proposalDismissed=false;
        if(!keepSchedule) {
            item.due=preview;
            item.plannedDue=item.due;
        }
        return item;
    }
    private static boolean keepsWeeklySchedule(Item item, boolean chore, LocalDate start, int weekday) {
        return item!=null&&item.chore&&chore&&item.weekday==weekday&&item.start.equals(start);
    }
    public LocalDate previewDue(String id, boolean chore, int cycle, LocalDate start, int weekday) {
        Item item=find(id);
        if(keepsWeeklySchedule(item,chore,start,weekday))return item.due;
        return chore?start.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.of(weekday))):start.plusDays(cycle);
    }
    public boolean completedToday(Item item) {
        return today().equals(item.completedOn)||(item.completedOn==null&&!item.history.isEmpty()&&item.history.get(0).date.equals(today()));
    }
    public LocalDate earliestCompletion(Item item) {
        LocalDate minimum=item.start;
        for(History event:item.history)if(event.date.isAfter(minimum))minimum=event.date;
        return minimum;
    }
    public boolean complete(String id) { return complete(id,today())!=null; }
    /** Local design receipt. Production completion/undo needs a server transaction. */
    public static final class CompletionReceipt {
        private final Item item;
        private final LocalDate start,due,plannedDue,completedOn;
        private final boolean proposalDismissed;
        private final int expectedRevision;
        private CompletionReceipt(Item item) {
            this.item=item;start=item.start;due=item.due;plannedDue=item.plannedDue;
            completedOn=item.completedOn;proposalDismissed=item.proposalDismissed;expectedRevision=item.revision+1;
        }
    }
    public CompletionReceipt complete(String id,LocalDate actualDate) {
        Item item=find(id);
        if(item==null||actualDate==null||actualDate.isAfter(today())
                ||actualDate.isBefore(earliestCompletion(item))||completedToday(item))return null;
        CompletionReceipt receipt=new CompletionReceipt(item);
        item.history.add(0,new History(actualDate,"나"));item.start=actualDate;item.completedOn=today();
        item.proposalDismissed=false;item.revision++;
        LocalDate anchor=actualDate.isAfter(item.plannedDue)?actualDate:item.plannedDue;
        item.due=item.chore?anchor.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.of(item.weekday))):actualDate.plusDays(item.cycle);
        item.plannedDue=item.due;return receipt;
    }
    public boolean undo(CompletionReceipt receipt) {
        if(receipt==null||find(receipt.item.id)!=receipt.item||receipt.item.revision!=receipt.expectedRevision)return false;
        Item item=receipt.item;item.history.remove(0);item.start=receipt.start;item.due=receipt.due;
        item.plannedDue=receipt.plannedDue;item.completedOn=receipt.completedOn;
        item.proposalDismissed=receipt.proposalDismissed;item.revision++;return true;
    }
    /** Tomorrow is the sole dry day in the three-day rain fixture, not real forecasting. */
    public LocalDate proposalTarget(Item item,boolean rain,boolean available) {
        LocalDate target=today().plusDays(1);
        if(item==null||!available||!rain||!item.chore||!item.weatherSensitive||item.proposalDismissed
                ||item.due.isBefore(today())||item.due.isAfter(today().plusDays(2))||item.due.equals(target))return null;
        return target;
    }
    public boolean delete(String id) { return items.removeIf(item -> item.id.equals(id)); }
    public boolean moveWeatherSuggestion(String id, LocalDate target) {
        Item item=find(id);
        if(item==null||!item.chore||!item.weatherSensitive||item.proposalDismissed
                ||target.isBefore(today())||target.equals(item.due))return false;
        item.due=target;item.revision++;return true;
    }
    public boolean dismissWeatherSuggestion(String id) {
        Item item=find(id);
        if(item==null||!item.chore||!item.weatherSensitive)return false;
        item.proposalDismissed=true;item.revision++;return true;
    }
}
