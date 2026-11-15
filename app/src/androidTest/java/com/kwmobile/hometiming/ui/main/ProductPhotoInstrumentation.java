package com.kwmobile.hometiming.ui.main;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.gms.tasks.Tasks;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.recognition.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real-photo smoke tests. Fixtures are externally supplied to this test package's private files. */
public final class ProductPhotoInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;
        try {
            String[][] fixtures={{"000000365385.jpg","칫솔"},{"000000311303.jpg","컵"},{"000000167353.jpg","시계"}};
            StringBuilder evidence=new StringBuilder();
            try(ProductObjectRecognizer objects=new ProductObjectRecognizer(getTargetContext())) {
                Bitmap blank=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);blank.eraseColor(Color.WHITE);
                try{check(objects.recognize(blank,0).isEmpty(),"blank photo must not invent a kind");}finally{blank.recycle();}
                for(String[] f:fixtures){
                    Bitmap bitmap=fixture(f[0]);check(bitmap!=null,"fixture missing "+f[0]);
                    List<String> names;
                    try{names=objects.recognize(bitmap,0);}finally{bitmap.recycle();}
                    evidence.append(f[0]).append(' ').append(names).append('\n');
                    if(!names.contains(f[1])) evidence.append("NOT RECOGNIZED expected ").append(f[1]).append('\n');
                }
                check(!evidence.toString().contains("NOT RECOGNIZED"),"development photos: "+evidence);
            }
            try(ProductPhotoRecognizer photo=new ProductPhotoRecognizer(getTargetContext())){
                Bitmap toothbrush=fixture(fixtures[0][0]);
                check(Tasks.await(photo.recognize(toothbrush,0),30,TimeUnit.SECONDS).contains("칫솔"),"full visual+OCR pipeline on a real toothbrush");
                Bitmap text=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(text);canvas.drawColor(Color.WHITE);
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(100);p.setTypeface(ResourcesCompat.getFont(getTargetContext(),R.font.pretendard_regular));
                canvas.drawText("ACME 샤워 필터",200,400,p);
                List<String> names=Tasks.await(photo.recognize(text,0),30,TimeUnit.SECONDS);
                check(names.contains("샤워필터") && names.stream().noneMatch(x->x.contains("ACME")),"OCR fallback returns a generic kind");
            }
            out.putString("stream","PHOTO KIND PASS: real model on three externally supplied COCO photos, blank rejection, combined toothbrush inference and OCR generic-name fallback. Smoke examples, NOT a real-user accuracy benchmark.\n"+evidence);ok=true;
        }catch(Throwable e){out.putString("stream","PHOTO KIND FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}
        finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
    private Bitmap fixture(String name) throws java.io.IOException {
        // Instrumentation runs as the target UID. A shell pipe reads only our known QA files
        // from the separate debuggable test package; no target storage/permissions are changed.
        try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                getUiAutomation().executeShellCommand("run-as com.kwmobile.hometiming.test cat files/"+name))){
            return BitmapFactory.decodeStream(in);
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
