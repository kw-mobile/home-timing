package com.kwmobile.hometiming.data.calendar;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import com.kwmobile.hometiming.domain.CalendarEntry;
import java.time.LocalDate;
import java.util.*;

/** Creates/removes only a uniquely named test calendar. Requires explicit adb permission grant. */
public final class CalendarProviderInstrumentation extends Instrumentation {
 private Bundle args;
 private final String account="home-timing-calendar-test-"+UUID.randomUUID();
 @Override public void onCreate(Bundle args){this.args=args;super.onCreate(args);start();}
 @Override public void onStart(){
  Bundle out=new Bundle(); Uri cal=null; boolean passed=false;
  try {
   ContentResolver cr=getTargetContext().getContentResolver();
   Uri calendars=CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true")
    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME,account).appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build();
   ContentValues c=new ContentValues(); c.put("account_name",account);c.put("account_type",CalendarContract.ACCOUNT_TYPE_LOCAL);c.put("name",account);c.put("calendar_displayName","HomeTiming disposable test");
   c.put("calendar_color",0xff336699);c.put("calendar_access_level",700);c.put("ownerAccount",account);c.put("visible",1);c.put("sync_events",1);c.put("calendar_timezone","Asia/Seoul");
   cal=cr.insert(calendars,c); long id=ContentUris.parseId(cal);
   DeviceCalendarStore store=new DeviceCalendarStore(getTargetContext());
   check(store.permitted(),"permissions");check(store.choices().stream().anyMatch(x->x.id==id),"writable choice");
   ContentValues foreign=new ContentValues();foreign.put("calendar_id",id);foreign.put("title","Personal untouched");foreign.put("dtstart",100000L);foreign.put("dtend",200000L);foreign.put("eventTimezone","UTC");
   Uri foreignUri=cr.insert(CalendarContract.Events.CONTENT_URI,foreign);
   String marker="HomeTiming/"+UUID.randomUUID()+"/";
   LocalDate today=LocalDate.of(2026,11,15);
   CalendarEntry row=new CalendarEntry("item:i:next","Brush",today,false);
   store.reconcile(id,marker,List.of(row));store.reconcile(id,marker,List.of(row));
   check(count(cr,id)==2,"idempotence and personal event retained");
   store.reconcile(id,marker,List.of(new CalendarEntry(row.key,"Brush",today.plusDays(3),false),new CalendarEntry("item:i:done","Brush",today,true)));
   check(count(cr,id)==3,"completion and next occurrence");
   try(Cursor rows=cr.query(CalendarContract.Events.CONTENT_URI,new String[]{"dtstart","dtend","allDay","eventTimezone","hasAlarm"},"calendar_id=? AND description=?",new String[]{""+id,marker+row.key},null)){
    check(rows.moveToFirst(),"projected event");check(rows.getLong(0)==today.plusDays(3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),"moved date");
    check(rows.getLong(1)-rows.getLong(0)==86400000L,"exclusive all-day end");check(rows.getInt(2)==1&&rows.getString(3).equals("UTC")&&rows.getInt(4)==0,"all day without alarm");
   }
   store.reconcile(id,marker,List.of());check(count(cr,id)==1,"disable/delete retains foreign event");
   try(Cursor f=cr.query(foreignUri,new String[]{"title"},null,null,null)){check(f.moveToFirst()&&f.getString(0).equals("Personal untouched"),"foreign event unchanged");}
   out.putString("stream","Calendar Provider PASS: create/idempotence/move/completion/delete/isolation/all-day/no alarm; configured Firebase only when explicitly requested\n");
   if ("configured".equals(args.getString("service"))) live(cr,id,store);
   passed=true;
  }catch(Throwable e){out.putString("stream","Calendar Provider FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}
  finally{if(cal!=null)getTargetContext().getContentResolver().delete(cal,null,null);}
  finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED,out);
 }
 private void live(ContentResolver cr,long id,DeviceCalendarStore store) throws Exception {
  if (!com.google.firebase.FirebaseApp.getInstance().getOptions().getProjectId().equals(args.getString("projectId"))) throw new AssertionError("Explicit project required");
  var auth=com.kwmobile.hometiming.data.auth.AuthRepository.getInstance();
  if(auth.isSignedIn() && "true".equals(args.getString("cleanupPreviousTest"))) {
   var previous=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
   check(previous!=null && previous.getEmail()!=null && previous.getEmail().startsWith("cal-production-")
    && previous.getEmail().endsWith("@example.test") && "CalendarSmoke".equals(previous.getDisplayName()),"only this runner's previous identity may be cleaned");
   try { com.google.android.gms.tasks.Tasks.await(auth.deleteAccount("Calendar-test-12345"),45,java.util.concurrent.TimeUnit.SECONDS); }
   catch(Exception error) {
    try { com.google.android.gms.tasks.Tasks.await(previous.reload(),15,java.util.concurrent.TimeUnit.SECONDS); throw error; }
    catch(java.util.concurrent.ExecutionException reload) {
     if (!(reload.getCause() instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException)) throw error;
    }
   }
   auth.signOut();
  }
  if(auth.isSignedIn()) throw new AssertionError("Dedicated signed-out test installation required");
  String password="Calendar-test-12345";
  var manager=DeviceCalendarManager.get(getTargetContext());
  boolean created=false;
  try {
   String email="cal-production-"+System.currentTimeMillis()+"@example.test";
   var session=com.google.android.gms.tasks.Tasks.await(auth.signUp("CalendarSmoke",email,password),45,java.util.concurrent.TimeUnit.SECONDS);created=true;
   var repo=com.kwmobile.hometiming.data.firestore.ItemRepository.getInstance();
   LocalDate today=LocalDate.now(com.kwmobile.hometiming.domain.DateRules.ZONE);
   var item=com.google.android.gms.tasks.Tasks.await(repo.create(session.houseId,new com.kwmobile.hometiming.domain.ItemDraft("CalendarBrush","bath",today.minusDays(1),30,"","","")),30,java.util.concurrent.TimeUnit.SECONDS);
   manager.start(session.uid,session.houseId);
   var choice=store.choices().stream().filter(x->x.id==id).findFirst().get();
   com.google.android.gms.tasks.Tasks.await(manager.enable(choice),45,java.util.concurrent.TimeUnit.SECONDS);
   check(count(cr,id)==2,"real Firebase item exported");
   item=com.google.android.gms.tasks.Tasks.await(repo.update(session.houseId,item,new com.kwmobile.hometiming.domain.ItemDraft("CalendarBrush edited","bath",today.minusDays(1),7,"","","")),30,java.util.concurrent.TimeUnit.SECONDS);
   com.google.android.gms.tasks.Tasks.await(manager.sync(),45,java.util.concurrent.TimeUnit.SECONDS);
   check(count(cr,id)==2,"update reuses event");
   com.google.android.gms.tasks.Tasks.await(repo.complete(session.houseId,item,today),30,java.util.concurrent.TimeUnit.SECONDS);
   com.google.android.gms.tasks.Tasks.await(manager.sync(),45,java.util.concurrent.TimeUnit.SECONDS);
   check(count(cr,id)==3,"real completion exported and next scheduled");
   com.google.android.gms.tasks.Tasks.await(repo.delete(session.houseId,item.id),30,java.util.concurrent.TimeUnit.SECONDS);
   com.google.android.gms.tasks.Tasks.await(manager.sync(),45,java.util.concurrent.TimeUnit.SECONDS);
   check(count(cr,id)==1,"real deletion retains personal event");
   com.google.android.gms.tasks.Tasks.await(manager.disable(),15,java.util.concurrent.TimeUnit.SECONDS);
   check(!manager.enabled(),"opt-out");
   com.google.android.gms.tasks.Tasks.await(repo.create(session.houseId,new com.kwmobile.hometiming.domain.ItemDraft("CleanupBrush","bath",today,30,"","","")),30,java.util.concurrent.TimeUnit.SECONDS);
   com.google.android.gms.tasks.Tasks.await(manager.enable(choice),45,java.util.concurrent.TimeUnit.SECONDS);
   check(count(cr,id)==2,"re-enabled single projection");
   com.google.android.gms.tasks.Tasks.await(manager.disable(),15,java.util.concurrent.TimeUnit.SECONDS);
   check(count(cr,id)==1,"opt-out removes populated projection only");
   com.google.android.gms.tasks.Tasks.await(manager.enable(choice),45,java.util.concurrent.TimeUnit.SECONDS);
   auth.signOut();
   check(!manager.enabled(),"account scope invalidation disables immediately");
   long until=System.currentTimeMillis()+15000;
   while(count(cr,id)!=1 && System.currentTimeMillis()<until) Thread.sleep(50);
   check(count(cr,id)==1,"sign-out cleanup preserves personal event");
   com.google.android.gms.tasks.Tasks.await(auth.signIn(email,password),45,java.util.concurrent.TimeUnit.SECONDS);
   check(!manager.enabled(),"sign-in does not silently re-enable opt-in");
  } finally {
   com.google.android.gms.tasks.Tasks.await(manager.disable(),15,java.util.concurrent.TimeUnit.SECONDS);
   manager.end();
   if(created) com.google.android.gms.tasks.Tasks.await(auth.deleteAccount(password),45,java.util.concurrent.TimeUnit.SECONDS);
   auth.signOut();
   waitForIdleSync(); Thread.sleep(500); // Let Firebase finish persisting sign-out before instrumentation exits.
  }
 }
 private static int count(ContentResolver cr,long id){try(Cursor c=cr.query(CalendarContract.Events.CONTENT_URI,new String[]{"_id"},"calendar_id=? AND deleted=0",new String[]{""+id},null)){return c.getCount();}}
 private static void check(boolean good,String name){if(!good)throw new AssertionError(name);}
}
