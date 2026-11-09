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
        public boolean chore, weatherSensitive;
        public int cycle, weekday;
        public LocalDate start, due;
        public final List<History> history = new ArrayList<>();
        public Item(String id, String name, String category, String location, boolean chore,
                    int cycle, LocalDate start, LocalDate due) {
            this.id=id; this.name=name; this.category=category; this.location=location;
            this.chore=chore; this.cycle=cycle; this.start=start; this.due=due;
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
        filter.history.add(new History(today.minusDays(60), "가족")); items.add(filter);
        Item bedding=new Item("bedding", "침구 세탁", "세탁", "침실", true, 7,
                today.minusDays(5), today.plusDays(2));
        bedding.history.add(new History(today.minusDays(5), "나")); items.add(bedding);
        items.add(new Item("sponge", "주방 수세미", "주방", "주방", false, 30,
                today.minusDays(20), today.plusDays(10)));
        items.add(new Item("clean", "욕실 청소", "청소", "욕실", true, 7,
                today.minusDays(3), today.plusDays(4)));
    }
    public Item find(String id) {
        for(Item item: items) if(item.id.equals(id)) {if(item.weekday==0)item.weekday=item.due.getDayOfWeek().getValue();return item;}
        return null;
    }
    public List<Item> sorted() {
        List<Item> result=new ArrayList<>(items);
        result.sort(Comparator.comparing(i -> i.due)); return result;
    }
    public Item save(String id, String name, String category, String location, boolean chore,
                     int cycle, LocalDate start, boolean weatherSensitive, int weekday) {
        Item item=find(id);
        if(item==null) { item=new Item(UUID.randomUUID().toString(),name,category,location,chore,cycle,start,start.plusDays(cycle)); items.add(item); }
        else {
            item.name=name; item.category=category; item.location=location; item.chore=chore;
            item.cycle=cycle; item.start=start; item.due=start.plusDays(cycle);
        }
        item.weatherSensitive=weatherSensitive;item.weekday=weekday;
        if(chore)item.due=start.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.of(weekday)));
        return item;
    }
    public boolean complete(String id) {
        Item item=find(id); if(item==null) return false;
        LocalDate today=today();
        // A repeated tap on the same demo day cannot create duplicate history entries.
        if(!item.history.isEmpty() && item.history.get(0).date.equals(today)) return false;
        item.history.add(0,new History(today,"나")); item.start=today;
        item.due=item.chore?today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.of(item.weekday))):today.plusDays(item.cycle);
        return true;
    }
    public boolean delete(String id) { return items.removeIf(item -> item.id.equals(id)); }
}
