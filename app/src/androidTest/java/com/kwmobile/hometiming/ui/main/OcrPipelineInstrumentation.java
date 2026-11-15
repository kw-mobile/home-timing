package com.kwmobile.hometiming.ui.main;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.tasks.Tasks;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.recognition.ProductTextRecognizer;
import com.kwmobile.hometiming.ui.recognition.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Synthetic packaging proves the offline pipeline, not real-product accuracy or camera quality. */
public final class OcrPipelineInstrumentation extends Instrumentation {
 private boolean modelOnly;
 @Override public void onCreate(Bundle args){modelOnly=args != null && "true".equals(args.getString("modelOnly"));super.onCreate(args);start();}
 @Override public void onStart(){Bundle out=new Bundle();boolean passed=false;MainActivity main=null;
  try {
   Bitmap bitmap=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888);
   Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
   Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTypeface(ResourcesCompat.getFont(getTargetContext(),R.font.pretendard_regular));
   p.setTextSize(100);canvas.drawText("샤워 필터",280,300,p);p.setTextSize(75);canvas.drawText("SHOWER FILTER",260,450,p);p.setTextSize(30);canvas.drawText("500 ml",520,750,p);
   List<String> candidates;
   try(ProductTextRecognizer recognizer=new ProductTextRecognizer()) { candidates=Tasks.await(recognizer.recognize(bitmap,0),30,TimeUnit.SECONDS); }
   check(candidates.stream().anyMatch(x->x.contains("필터")),"bundled Korean pipeline");
   check(candidates.stream().anyMatch(x->x.toUpperCase(Locale.ROOT).contains("FILTER")),"bundled Latin pipeline");
   if(modelOnly){out.putString("stream","OCR MODEL PASS: bundled Korean+Latin synthetic bitmap; no UI/camera/real-package accuracy claim.\n");finish(Activity.RESULT_OK,out);return;}
   main=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("preview_screen","editor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   MainActivity activity=main;
   DesignPreviewViewModel state=onMain(()->new ViewModelProvider(activity).get(DesignPreviewViewModel.class));
   LocalDate date=onMain(()->state.state.draft.date);String cycle=onMain(()->state.state.draft.cycle);
   PhotoNameActivity photo=openPhoto(activity);
   PhotoNameViewModel vm=onMain(()->new ViewModelProvider(photo).get(PhotoNameViewModel.class));
   runOnMainSync(()->{int old=vm.begin();vm.cancelProcessing();int next=vm.begin();check(!vm.current(old)&&vm.current(next),"late request rejected");vm.fail(R.string.ocr_empty);});
   // Supply the actual model's synthetic-image result to test explicit confirmation and form return.
   runOnMainSync(()->{vm.result=true;vm.busy=false;vm.candidates=candidates;vm.message=R.string.ocr_choose;vm.select(candidates.get(0));});
   runOnMainSync(()->photo.findViewById(R.id.ocr_apply).performClick());waitForIdleSync();
   check(onMain(()->state.state.draft.name.equals(candidates.get(0))),"explicit apply returns name");
   check(onMain(()->state.state.draft.date.equals(date)&&state.state.draft.cycle.equals(cycle)),"date/cycle preserved");
   PhotoNameActivity stale=openPhoto(activity);
   PhotoNameViewModel staleVm=onMain(()->new ViewModelProvider(stale).get(PhotoNameViewModel.class));
   runOnMainSync(()->{state.state.draft=new DesignPreviewViewModel.Draft();state.state.draft.name="Keep new draft";
    staleVm.result=true;staleVm.busy=false;staleVm.candidates=candidates;staleVm.select(candidates.get(0));
    stale.findViewById(R.id.ocr_apply).performClick();});waitForIdleSync();
   check(onMain(()->state.state.draft.name.equals("Keep new draft")),"old result cannot overwrite another draft");
   out.putString("stream","OCR PASS: bundled Korean+Latin synthetic bitmap; explicit apply; date/cycle preservation; cancellation/stale request and draft guards. Real packaging/camera accuracy NOT tested.\n");passed=true;
  }catch(Throwable e){out.putString("stream","OCR FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}
  finally{if(main!=null){MainActivity done=main;runOnMainSync(done::finish);}}
  finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
 }
 private PhotoNameActivity openPhoto(MainActivity a){
  ActivityMonitor monitor=addMonitor(PhotoNameActivity.class.getName(),null,false);
  runOnMainSync(()->a.findViewById(R.id.editor_photo_name).performClick());
  PhotoNameActivity result=(PhotoNameActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
  check(result!=null,"photo screen opened");waitForIdleSync();return result;
 }
 private <T>T onMain(Supplier<T> work){AtomicReference<T> ref=new AtomicReference<>();runOnMainSync(()->ref.set(work.get()));return ref.get();}
 private static void check(boolean good,String name){if(!good)throw new AssertionError(name);}
}
