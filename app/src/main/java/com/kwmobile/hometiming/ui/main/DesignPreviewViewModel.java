package com.kwmobile.hometiming.ui.main;

import android.os.Bundle;
import androidx.lifecycle.ViewModel;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Navigation, form drafts and fixture state. Contains no View or Activity references. */
public final class DesignPreviewViewModel extends ViewModel {
    public static final class Route implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String screen, id;
        public Route(String screen) { this(screen, ""); }
        public Route(String screen,String id) { this.screen=screen; this.id=id; }
        public String key() { return screen+":"+id; }
    }
    public static final class Draft implements Serializable {
        private static final long serialVersionUID = 1L;
        public String id="", name="", location="", category="기타", cycle="60";
        public LocalDate date=PreviewStore.today();
        public int weekday=date.getDayOfWeek().getValue();
        public boolean chore=false, weather=false, dirty=false;
    }
    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        public PreviewStore store=new PreviewStore();
        public Route route=new Route("home");
        public ArrayDeque<Route> back=new ArrayDeque<>();
        public Map<String,Integer> scrolls=new HashMap<>();
        public Map<String,String> authFields=new HashMap<>();
        public Draft draft;
        public String filter="전체", weather="auto", condition="clear", scenario="normal";
        public String region="서울 마포구", person="나", time="09:00", invite="284619", joinCode="";
        public int members=2;
        public LocalDate selected=PreviewStore.today();
        public YearMonth month=YearMonth.from(selected);
        public boolean expanded=false, owner=true, joined=false, notifications=false;
        public boolean passwordVisible=false, initialized=false;
        public int advanceDays=3;
    }
    public State state=new State();
    @SuppressWarnings("deprecation")
    public void restore(Bundle saved) {
        if(!state.initialized && saved!=null) {
            Serializable value=saved.getSerializable("preview_state");
            if(value instanceof State) state=(State)value;
        }
        if(state.members==0)state.members=2;
        if(state.joinCode==null)state.joinCode="";
        state.initialized=true;
    }
    public void save(Bundle out) { out.putSerializable("preview_state",state); }
    public void navigate(String screen,String id) { state.back.push(state.route);state.route=new Route(screen,id); }
    public void tab(String screen) { state.back.clear();state.route=new Route(screen); }
    public boolean back() {
        if(!state.back.isEmpty()) { state.route=state.back.pop();return true; }
        if(!state.route.screen.equals("home")) { state.route=new Route("home");return true; }
        return false;
    }
    public void edit(String id) {
        Draft d=new Draft(); PreviewStore.Item item=state.store.find(id);
        if(item!=null) {
            d.id=item.id;d.name=item.name;d.location=item.location;d.category=item.category;
            d.weekday=item.weekday;d.chore=item.chore;d.weather=item.weatherSensitive;d.cycle=String.valueOf(item.cycle);d.date=item.start;
        }
        state.draft=d;state.scrolls.remove(new Route("editor",id).key());navigate("editor",id);
    }
}
