package com.kwmobile.hometiming.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.core.content.ContextCompat;

import com.kwmobile.hometiming.R;

/** External top-edge shadow. Position 24dp above the sheet; the sheet covers its fill. */
public final class SheetShadowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density = getResources().getDisplayMetrics().density;
    private View sheet;
    private ViewTreeObserver observer;
    private final ViewTreeObserver.OnPreDrawListener follow =
            () -> {
                if (sheet != null && sheet.getParent() instanceof View) {
                    setY(sheet.getY() - 24 * density);
                    setVisibility(
                            !sheet.isShown()
                                            || sheet.getY()
                                                    >= ((View) sheet.getParent()).getHeight()
                                    ? INVISIBLE
                                    : VISIBLE);
                } else setVisibility(INVISIBLE);
                return true;
            };

    public void follow(View sheet) {
        this.sheet = sheet;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observer = getViewTreeObserver();
        observer.addOnPreDrawListener(follow);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(follow);
        observer = null;
        super.onDetachedFromWindow();
    }

    public SheetShadowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
        paint.setColor(ContextCompat.getColor(context, R.color.ht_surface));
        paint.setShadowLayer(
                14 * density,
                0,
                -2 * density,
                ContextCompat.getColor(context, R.color.ht_sheet_shadow));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRoundRect(
                0,
                24 * density,
                getWidth(),
                getHeight() + 24 * density,
                24 * density,
                24 * density,
                paint);
    }
}
