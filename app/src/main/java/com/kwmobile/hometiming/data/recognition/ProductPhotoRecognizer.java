package com.kwmobile.hometiming.data.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.camera.core.ImageProxy;
import com.google.android.gms.tasks.*;
import com.kwmobile.hometiming.domain.HouseholdNames;
import java.util.*;
import java.util.concurrent.*;

/** One owned bitmap: visual general nouns first, OCR general-noun fallback second. */
public final class ProductPhotoRecognizer implements AutoCloseable {
    private final ProductObjectRecognizer objects;
    private final ProductTextRecognizer text=new ProductTextRecognizer();
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private boolean active,closed;
    public ProductPhotoRecognizer(Context context){objects=new ProductObjectRecognizer(context);}
    private synchronized boolean acquire(){if(active||closed)return false;active=true;return true;}
    private Task<List<String>> owned(Task<List<String>> task){
        task.addOnCompleteListener(Runnable::run,ignored->{synchronized(this){active=false;if(closed)release();}});return task;
    }
    public Task<List<String>> recognize(ImageProxy proxy){
        if(!acquire()){proxy.close();return Tasks.forException(new IllegalStateException("Recognition already active"));}
        int rotation=proxy.getImageInfo().getRotationDegrees();
        return owned(Tasks.call(images,()->{try{return proxy.toBitmap();}finally{proxy.close();}}).onSuccessTask(bitmap->process(bitmap,rotation)));
    }
    /** Takes ownership, including failure paths. Also used by offline photo integration tests. */
    public Task<List<String>> recognize(Bitmap bitmap,int rotation){
        if(!acquire()){bitmap.recycle();return Tasks.forException(new IllegalStateException("Recognition already active"));}
        return owned(process(bitmap,rotation));
    }
    private Task<List<String>> process(Bitmap bitmap,int rotation){
        return Tasks.call(images,()->objects.recognize(bitmap,rotation)).continueWithTask(done->{
            List<String> visual=done.isSuccessful()?done.getResult():Collections.emptyList();
            // OCR owns and releases the original bitmap once visual inference has finished.
            return text.recognize(bitmap,rotation).continueWith(read->{
                if(!done.isSuccessful()&&!read.isSuccessful())throw new IllegalStateException("Photo recognition unavailable");
                return HouseholdNames.combine(visual,read.isSuccessful()?HouseholdNames.text(read.getResult()):Collections.emptyList());
            });
        });
    }
    private void release(){objects.close();text.close();images.shutdown();}
    @Override public synchronized void close(){if(closed)return;closed=true;if(!active)release();}
}
