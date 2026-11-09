package com.kwmobile.hometiming.ui.component;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

/** Blurs a sampled copy of content behind the bar, never the navigation labels or icons. */
public final class GlassBackdropView extends View {
    private static final int SAMPLE_SCALE = 4;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect destination = new Rect();
    private final int[] sourceLocation = new int[2];
    private final int[] backdropLocation = new int[2];
    private View source;
    private Bitmap backdrop;
    private boolean updatePending;

    public GlassBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float radius = 12 * getResources().getDisplayMetrics().density;
            setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }
    }

    public void setSource(View content) {
        source = content;
        refresh();
    }

    public void refresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || updatePending) {
            return;
        }
        updatePending = true;
        postOnAnimation(() -> {
            updatePending = false;
            captureBackdrop();
        });
    }

    private void captureBackdrop() {
        if (!isAttachedToWindow() || source == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        int width = Math.max(1, getWidth() / SAMPLE_SCALE);
        int height = Math.max(1, getHeight() / SAMPLE_SCALE);
        if (backdrop == null || backdrop.getWidth() != width || backdrop.getHeight() != height) {
            backdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        backdrop.eraseColor(0);
        source.getLocationInWindow(sourceLocation);
        getLocationInWindow(backdropLocation);
        Canvas canvas = new Canvas(backdrop);
        canvas.scale(1f / SAMPLE_SCALE, 1f / SAMPLE_SCALE);
        canvas.translate(sourceLocation[0] - backdropLocation[0],
                sourceLocation[1] - backdropLocation[1]);
        source.draw(canvas);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        destination.set(0, 0, width, height);
        refresh();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (backdrop != null) {
            canvas.drawBitmap(backdrop, null, destination, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        backdrop = null;
    }
}
