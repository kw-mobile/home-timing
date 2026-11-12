package com.kwmobile.hometiming.ui.component;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.WeakHashMap;

/** Analytic critically damped springs without an extra animation dependency. */
public final class FluidMotion {
    private FluidMotion() {}

    private static final WeakHashMap<View, Spring> springs = new WeakHashMap<>();

    public static boolean enabled(View view) {
        return Settings.Global.getFloat(
                                view.getContext().getContentResolver(),
                                Settings.Global.ANIMATOR_DURATION_SCALE,
                                1f)
                        > 0
                && ValueAnimator.areAnimatorsEnabled();
    }

    @android.annotation.SuppressLint(
            "ClickableViewAccessibility") // Returns false: native controls retain performClick and
                                          // accessibility semantics.
    public static void press(View view) {
        view.setOnTouchListener(
                (v, event) -> {
                    if (!v.isEnabled()) return false;
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            scale(v, .975f);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            scale(v, 1f);
                            break;
                        default:
                            break;
                    }
                    return false;
                });
    }

    public static void enter(View view, Runnable redrawBackdrop) {
        if (!enabled(view)) return;
        float distance = 8 * view.getResources().getDisplayMetrics().density;
        long began = SystemClock.uptimeMillis();
        view.setTranslationY(distance);
        view.setAlpha(.96f);
        Choreographer.getInstance()
                .postFrameCallback(
                        new Choreographer.FrameCallback() {
                            @Override
                            public void doFrame(long frameTime) {
                                if (!view.isAttachedToWindow()) return;
                                float elapsed = (SystemClock.uptimeMillis() - began) / 1000f;
                                float remaining = CriticalSpring.displacement(1, 0, elapsed, 26);
                                view.setTranslationY(distance * remaining);
                                view.setAlpha(1 - .04f * remaining);
                                redrawBackdrop.run();
                                if (elapsed < .45f)
                                    Choreographer.getInstance().postFrameCallback(this);
                                else {
                                    view.setTranslationY(0);
                                    view.setAlpha(1);
                                    redrawBackdrop.run();
                                }
                            }
                        });
    }

    private static void scale(View view, float target) {
        Spring previous = springs.remove(view);
        if (previous != null) previous.cancelled = true;
        if (!enabled(view)) {
            view.setScaleX(1);
            view.setScaleY(1);
            return;
        }
        Spring spring = new Spring(view, target, previous == null ? 0 : previous.velocity);
        springs.put(view, spring);
        Choreographer.getInstance().postFrameCallback(spring);
    }

    private static final class Spring implements Choreographer.FrameCallback {
        final View view;
        final float target, start, initialVelocity;
        float velocity;
        final long began = SystemClock.uptimeMillis();
        boolean cancelled;

        Spring(View view, float target, float initialVelocity) {
            this.view = view;
            this.target = target;
            this.initialVelocity = initialVelocity;
            start = view.getScaleX();
        }

        @Override
        public void doFrame(long frameTime) {
            if (cancelled || !view.isAttachedToWindow()) {
                if (springs.get(view) == this) springs.remove(view);
                return;
            }
            if (!enabled(view)) {
                view.setScaleX(1);
                view.setScaleY(1);
                springs.remove(view);
                return;
            }
            float t = (SystemClock.uptimeMillis() - began) / 1000f;
            float omega = 26f;
            float value =
                    target + CriticalSpring.displacement(start - target, initialVelocity, t, omega);
            velocity = CriticalSpring.velocity(start - target, initialVelocity, t, omega);
            view.setScaleX(value);
            view.setScaleY(value);
            if (Math.abs(value - target) > .0002f && t < .5f)
                Choreographer.getInstance().postFrameCallback(this);
            else {
                view.setScaleX(target);
                view.setScaleY(target);
                springs.remove(view);
            }
        }
    }
}
