package com.kwmobile.hometiming.ui.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kwmobile.hometiming.R;

/** Presentation only. The caller supplies forecast text/state and handles every event. */
public final class WeatherProposalView extends LinearLayout {
    public enum State { SUGGESTED, KEPT, UNCHANGED, UNAVAILABLE, OUTSIDE_RANGE }
    public WeatherProposalView(Context context) { this(context,null); }
    public WeatherProposalView(Context context,AttributeSet attrs) {
        super(context,attrs);setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.component_weather_proposal,this,true);
        androidx.core.view.ViewCompat.setAccessibilityHeading(findViewById(R.id.proposal_title),true);
        FluidMotion.press(findViewById(R.id.proposal_change));FluidMotion.press(findViewById(R.id.proposal_keep));
    }
    public void bind(State state,String title,String reason,String changeLabel,boolean busy,
                     Runnable change,Runnable keep,Runnable details) {
        ((TextView)findViewById(R.id.proposal_title)).setText(title);
        ((TextView)findViewById(R.id.proposal_reason)).setText(reason);
        ((TextView)findViewById(R.id.proposal_change)).setText(changeLabel);
        findViewById(R.id.proposal_choices).setVisibility(state==State.SUGGESTED?VISIBLE:GONE);
        View changeView=findViewById(R.id.proposal_change),keepView=findViewById(R.id.proposal_keep);
        changeView.setEnabled(!busy&&change!=null);keepView.setEnabled(!busy&&keep!=null);
        changeView.setOnClickListener(v -> {if(change!=null)change.run();});
        keepView.setOnClickListener(v -> {if(keep!=null)keep.run();});
        View detailsView=findViewById(R.id.proposal_details);detailsView.setVisibility(details==null?GONE:VISIBLE);
        detailsView.setOnClickListener(v -> {if(details!=null)details.run();});
    }
}
