package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.ui.component.FluidMotion;

final class PreviewItemRows {
    private final Context context;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;

    PreviewItemRows(
            View root, DesignPreviewViewModel.State preview, PreviewHost host, PreviewWidgets ui) {
        context = root.getContext();
        this.preview = preview;
        this.host = host;
        this.ui = ui;
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    void bind(LinearLayout parent, Item item, boolean swipe) {
        View row =
                android.view.LayoutInflater.from(context)
                        .inflate(R.layout.component_item_row, parent, false);
        ((TextView) row.findViewById(R.id.row_title)).setText(item.name);
        ((TextView) row.findViewById(R.id.row_meta))
                .setText(
                        context.getString(
                                R.string.preview_item_meta,
                                item.location.isEmpty() ? item.category : item.location,
                                item.chore
                                        ? context.getString(R.string.preview_copy_024)
                                        : context.getString(
                                                R.string.preview_cycle_days, item.cycle)));
        TextView status = row.findViewById(R.id.row_status);
        status.setText(ui.status(item));
        status.setTextColor(ui.color(ui.statusColor(item)));
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(ui.icon(item));
        row.setOnClickListener(v -> host.go("detail", item.id));
        if (swipe)
            row.setOnTouchListener(
                    new View.OnTouchListener() {
                        float x, y;
                        boolean horizontal;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                x = event.getRawX();
                                y = event.getRawY();
                                horizontal = false;
                            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                                float dx = event.getRawX() - x, dy = event.getRawY() - y;
                                if (Math.abs(dx) > ui.dp(16) && Math.abs(dx) > Math.abs(dy) * 1.5f)
                                    horizontal = true;
                                if (horizontal) {
                                    v.setPressed(false);
                                    v.cancelLongPress();
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                    v.setTranslationX(
                                            Math.max(-ui.dp(110), Math.min(ui.dp(24), dx * .55f)));
                                    return true;
                                }
                            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                                boolean complete =
                                        event.getActionMasked() == MotionEvent.ACTION_UP
                                                && horizontal
                                                && event.getRawX() - x < -ui.dp(90);
                                if (horizontal) {
                                    v.setPressed(false);
                                    v.cancelLongPress();
                                }
                                if (FluidMotion.enabled(v))
                                    v.animate().translationX(0).setDuration(180).start();
                                else v.setTranslationX(0);
                                if (complete) host.recordCompletion(item, PreviewStore.today());
                                return horizontal;
                            }
                            return false;
                        }
                    });
        if (preview.route.screen.equals("home")) {
            View quick = row.findViewById(R.id.row_complete);
            quick.setVisibility(View.VISIBLE);
            quick.setContentDescription(
                    context.getString(R.string.component_quick_complete, item.name));
            quick.setEnabled(
                    preview.scenario.equals("normal") && !preview.store.completedToday(item));
            quick.setOnClickListener(v -> host.recordCompletion(item, PreviewStore.today()));
            FluidMotion.press(quick);
        }
        parent.addView(row);
        ui.line(parent);
    }
}
