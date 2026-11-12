package com.kwmobile.hometiming.ui.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kwmobile.hometiming.R;

/** Date affordance only. Caller owns selected date, allowed range and picker lifecycle. */
public final class CompletionDateView extends LinearLayout {
    public CompletionDateView(Context context) {
        this(context, null);
    }

    public CompletionDateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.component_completion_date, this, true);
    }

    public void bind(String value, boolean enabled, Runnable change) {
        ((TextView) findViewById(R.id.completion_date_value)).setText(value);
        View action = findViewById(R.id.completion_date_action);
        action.setEnabled(enabled);
        action.setOnClickListener(
                v -> {
                    if (change != null) change.run();
                });
    }
}
