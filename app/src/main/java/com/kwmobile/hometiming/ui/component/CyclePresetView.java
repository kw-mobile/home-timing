package com.kwmobile.hometiming.ui.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.kwmobile.hometiming.R;
import java.util.function.IntConsumer;

/** Renders caller-supplied labels and emits an index. Never decides recommended durations. */
public final class CyclePresetView extends LinearLayout {
    public CyclePresetView(Context context) { this(context,null); }
    public CyclePresetView(Context context,AttributeSet attrs) {super(context,attrs);setOrientation(VERTICAL);}
    public void bind(String[] labels,IntConsumer selected) {
        removeAllViews();
        TextView help=new TextView(getContext());help.setTextAppearance(getContext(),R.style.TextAppearance_HomeTiming_Caption);
        help.setText(R.string.component_preset_title);addView(help);
        HorizontalScrollView scroll=new HorizontalScrollView(getContext());scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices=new LinearLayout(getContext());choices.setOrientation(HORIZONTAL);
        int space=getResources().getDimensionPixelSize(R.dimen.ht_space_8);
        for(int index=0;index<labels.length;index++) {
            final int value=index;
            MaterialButton button=(MaterialButton)LayoutInflater.from(getContext()).inflate(R.layout.component_preset_option,choices,false);
            button.setText(labels[index]);button.setOnClickListener(v -> selected.accept(value));FluidMotion.press(button);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);lp.setMarginEnd(space);choices.addView(button,lp);
        }
        scroll.addView(choices);LayoutParams lp=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);lp.topMargin=space;addView(scroll,lp);
    }
}
