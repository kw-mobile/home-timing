package com.kwmobile.hometiming.data.recognition;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.camera.core.ImageProxy;
import com.google.android.gms.tasks.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.kwmobile.hometiming.domain.ProductNameCandidates;
import java.util.*;
import java.util.concurrent.*;

/** Bundled models only. No photo files, upload, HTTP logging or raw text persistence. */
public final class ProductTextRecognizer implements AutoCloseable {
    private final TextRecognizer korean = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    private final TextRecognizer latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private boolean active, closed;
    private synchronized boolean acquire() { if (active || closed) return false; active = true; return true; }
    private <T> Task<T> owned(Task<T> task) {
        task.addOnCompleteListener(Runnable::run, ignored -> { synchronized (this) {
            active = false; if (closed) release();
        }});
        return task;
    }
    public Task<List<String>> recognize(ImageProxy proxy) {
        if (!acquire()) { proxy.close(); return Tasks.forException(new IllegalStateException("Recognition already active")); }
        int rotation = proxy.getImageInfo().getRotationDegrees();
        return owned(Tasks.call(images, () -> {
            try { return proxy.toBitmap(); } finally { proxy.close(); }
        }).onSuccessTask(bitmap -> processBitmap(bitmap, rotation)));
    }
    /** Takes ownership of the bitmap; used by camera conversion and the offline pipeline test. */
    public Task<List<String>> recognize(Bitmap bitmap, int rotation) {
        if (!acquire()) { bitmap.recycle(); return Tasks.forException(new IllegalStateException("Recognition already active")); }
        return owned(processBitmap(bitmap, rotation));
    }
    private Task<List<String>> processBitmap(Bitmap bitmap, int rotation) {
        InputImage image = InputImage.fromBitmap(bitmap, rotation);
        Task<Text> a = korean.process(image), b = latin.process(image);
        return Tasks.whenAllComplete(a, b).continueWith(images, done -> {
            try {
                if (!a.isSuccessful() && !b.isSuccessful()) throw new IllegalStateException("Text recognition unavailable");
                List<ProductNameCandidates.Line> lines = new ArrayList<>();
                int width = rotation % 180 == 0 ? bitmap.getWidth() : bitmap.getHeight();
                int height = rotation % 180 == 0 ? bitmap.getHeight() : bitmap.getWidth();
                if (a.isSuccessful()) collect(a.getResult(), width, height, lines);
                if (b.isSuccessful()) collect(b.getResult(), width, height, lines);
                return ProductNameCandidates.select(lines);
            } finally { bitmap.recycle(); }
        });
    }
    private static void collect(Text text, int width, int height, List<ProductNameCandidates.Line> target) {
        for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines()) {
            Rect box = line.getBoundingBox();
            if (box != null) target.add(new ProductNameCandidates.Line(line.getText(), box.exactCenterX()/width,
                    box.exactCenterY()/height, (double)box.height()/height));
        }
    }
    private void release() { korean.close(); latin.close(); images.shutdown(); }
    @Override public synchronized void close() { closed = true; if (!active) release(); }
}
