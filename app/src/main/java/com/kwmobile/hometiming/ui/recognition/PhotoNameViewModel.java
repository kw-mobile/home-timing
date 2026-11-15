package com.kwmobile.hometiming.ui.recognition;

import android.os.Handler;
import android.os.Looper;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.*;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.recognition.ProductPhotoRecognizer;
import android.app.Application;
import java.util.*;

public final class PhotoNameViewModel extends AndroidViewModel {
    private final ProductPhotoRecognizer recognizer;
    public PhotoNameViewModel(Application app) { super(app); recognizer = new ProductPhotoRecognizer(app); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private int generation;
    public boolean busy, result;
    public int message = R.string.ocr_guide;
    public List<String> candidates = Collections.emptyList();
    public String selected = "";
    public LiveData<Integer> changes() { return changes; }
    public int begin() {
        generation++; busy = true; result = false; message = R.string.ocr_processing; changed();
        int token = generation;
        main.postDelayed(() -> { if (current(token)) fail(R.string.ocr_timeout); }, 8000);
        return token;
    }
    public boolean current(int token) { return token == generation && busy; }
    public void analyze(int token, ImageProxy proxy) {
        if (!current(token)) { proxy.close(); return; }
        recognizer.recognize(proxy).addOnCompleteListener(done -> {
            if (!current(token)) return;
            busy = false;
            if (!done.isSuccessful()) { message = R.string.ocr_failed; result = false; }
            else {
                candidates = done.getResult(); result = !candidates.isEmpty(); selected = "";
                message = result ? R.string.ocr_choose : R.string.ocr_empty;
            }
            changed();
        });
    }
    public void fail(int text) { generation++; busy = false; result = false; candidates = Collections.emptyList(); message = text; changed(); }
    public void cancelProcessing() { if (busy) fail(R.string.ocr_interrupted); }
    public void ready() { generation++; busy = false; result = false; message = R.string.ocr_guide; changed(); }
    public void select(String name) { selected = name; changed(); }
    private void changed() { changes.setValue(changes.getValue() + 1); }
    @Override protected void onCleared() { generation++; main.removeCallbacksAndMessages(null); recognizer.close(); }
}
