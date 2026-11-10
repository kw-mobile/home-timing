package com.kwmobile.hometiming.ui.component;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Samples only content behind the bar; the navigation labels and icons stay sharp. */
public final class GlassBackdropView extends View {
    private static final int SAMPLE_SCALE = 4;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect destination = new Rect();
    private final int[] viewLocation = new int[2];
    private final int[] backdropLocation = new int[2];
    private View source;
    private Bitmap backdrop;
    private boolean updatePending;
    private final LensRenderer lens;
    private final int samplePadding;
    private int[] blurPixels;
    private int[] blurScratch;

    public GlassBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        float density = getResources().getDisplayMetrics().density;
        lens = Build.VERSION.SDK_INT >= 33 ? new LensRenderer(density) : null;
        samplePadding = lens == null ? 0 : Math.round(20 * density);
        if (lens == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float radius = 16 * getResources().getDisplayMetrics().density;
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
        int width = Math.max(1, (getWidth() + 2 * samplePadding + SAMPLE_SCALE - 1) / SAMPLE_SCALE);
        int height = Math.max(1, (getHeight() + 2 * samplePadding + SAMPLE_SCALE - 1) / SAMPLE_SCALE);
        if (backdrop == null || backdrop.getWidth() != width || backdrop.getHeight() != height) {
            backdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        backdrop.eraseColor(0);
        getLocationInWindow(backdropLocation);
        backdropLocation[0] -= samplePadding;
        backdropLocation[1] -= samplePadding;
        Canvas canvas = new Canvas(backdrop);
        canvas.scale(1f / SAMPLE_SCALE, 1f / SAMPLE_SCALE);
        source.getLocationInWindow(viewLocation);
        canvas.translate(viewLocation[0] - backdropLocation[0], viewLocation[1] - backdropLocation[1]);
        source.draw(canvas);
        if (Build.VERSION.SDK_INT >= 33 && lens != null) {
            if (blurPixels == null || blurPixels.length != width * height) {
                blurPixels = new int[width * height];
                blurScratch = new int[width * height];
            }
            backdrop.getPixels(blurPixels, 0, width, 0, 0, width, height);
            int radius = Math.max(1, Math.round(6 * getResources().getDisplayMetrics().density / SAMPLE_SCALE));
            GlassBlur.apply(blurPixels, blurScratch, width, height, radius);
            backdrop.setPixels(blurPixels, 0, width, 0, 0, width, height);
            lens.update(backdrop, getWidth(), getHeight(), samplePadding);
        }
        invalidateContent(source);
        invalidate();
    }

    // Software snapshots can consume display-list dirty flags. Restore them for the
    // complete source so unchanged siblings remain visible in the hardware frame.
    private void invalidateContent(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateContent(group.getChildAt(i));
            }
        }
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
            if (Build.VERSION.SDK_INT >= 33 && lens != null) {
                lens.draw(canvas, getWidth(), getHeight());
            } else {
                canvas.drawBitmap(backdrop, null, destination, paint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        backdrop = null;
        blurPixels = null;
        blurScratch = null;
        if (Build.VERSION.SDK_INT >= 33 && lens != null) {
            lens.release();
        }
    }

    /** API 33+ optical treatment. Bounded to the bar, with no continuous animation loop. */
    @androidx.annotation.RequiresApi(33)
    private static final class LensRenderer {
        private static final String PROGRAM = """
                uniform shader scene;
                uniform float2 size;
                uniform float density;
                uniform float padding;
                uniform float sampleScale;

                half4 sampleScene(float2 p) {
                    return scene.eval((p + padding) / sampleScale);
                }
                half4 main(float2 coord) {
                    float2 halfSize = size * 0.5;
                    float radius = halfSize.y;
                    float2 p = coord - halfSize;
                    float2 axis = float2(clamp(p.x, -halfSize.x + radius, halfSize.x - radius), 0);
                    float2 radial = p - axis;
                    float distance = length(radial) - radius;
                    float depth = -distance;
                    float2 normal = radial / max(length(radial), 0.001);
                    float edge = 1.0 - smoothstep(0.0, 14.0 * density, depth);
                    float2 refracted = coord - normal * (8.0 * density * edge * edge);
                    half3 rgb = sampleScene(refracted).rgb;
                    float light = max(dot(normal, float2(-0.6,-0.8)), 0.0);
                    float rim = 1.0 - smoothstep(0.4 * density, 1.5 * density, depth);
                    rgb = mix(rgb, half3(1.0), half(rim * (0.10 + 0.48 * light)));
                    rgb *= half(1.0 - rim * 0.12 * (1.0 - light));
                    float coverage = 1.0 - smoothstep(-1.0, 0.0, distance);
                    return half4(clamp(rgb, 0.0, 1.0) * half(coverage), half(coverage));
                }
                """;
        private final RuntimeShader shader = new RuntimeShader(PROGRAM);
        private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap image;

        LensRenderer(float density) {
            shader.setFloatUniform("density", density);
            shader.setFloatUniform("sampleScale", SAMPLE_SCALE);
            lensPaint.setShader(shader);
        }

        void update(Bitmap bitmap, int width, int height, int padding) {
            if (image != bitmap) {
                image = bitmap;
                shader.setInputShader("scene", new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            }
            shader.setFloatUniform("size", width, height);
            shader.setFloatUniform("padding", padding);
        }

        void draw(Canvas canvas, int width, int height) {
            canvas.drawRect(0, 0, width, height, lensPaint);
        }

        void release() {
            image = null;
        }
    }
}
