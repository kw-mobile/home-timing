package com.kwmobile.hometiming.ui.main;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import androidx.lifecycle.ViewModelProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Device integration runner. Defaults to demo emulators; configured-project tests require explicit project/run guards. */
public final class MvpFlowInstrumentation extends Instrumentation {
    private MainActivity activity;
    private MvpViewModel live;
    private DesignPreviewViewModel ui;
    private Bundle args;
    private String run, role;
    // Disposable credentials for explicitly guarded test accounts only. Never use for real users.
    private static final String PASSWORD = "Demo-only-12345";

    @Override public void onCreate(Bundle arguments) {
        args = arguments;
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            run = args.getString("runId", "local");
            role = args.getString("role", "owner");
            if (!run.matches("[a-zA-Z0-9_-]{1,40}")) throw new AssertionError("Invalid demo run id");
            if (!com.kwmobile.hometiming.BuildConfig.FIREBASE_CONFIGURED)
                throw new AssertionError("Configured build required; see docs/TESTING.md");
            boolean configured = "configured".equals(args.getString("service"));
            if (configured) {
                if (!run.startsWith("mvp-production-") || !FirebaseApp.getInstance().getOptions().getProjectId()
                        .equals(args.getString("projectId"))) throw new AssertionError("Explicit production test project/run guards required");
                if (FirebaseAuth.getInstance().getCurrentUser() != null
                        && !(role.equals("ocr-cleanup") && (run + "-ocr@example.test")
                                .equals(FirebaseAuth.getInstance().getCurrentUser().getEmail())))
                    throw new AssertionError("Dedicated test installation with no existing account required");
            } else {
                if ((getTargetContext().getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0)
                    throw new AssertionError("Release tests require the explicit configured-project guards; cleartext remains disabled");
                runOnMainSync(() -> {
                for (FirebaseApp app : FirebaseApp.getApps(getTargetContext())) app.delete();
                FirebaseApp.initializeApp(getTargetContext(), new FirebaseOptions.Builder()
                        .setProjectId("demo-home-timing").setApplicationId("1:123456789:android:abcdef123456")
                        .setApiKey("demo-emulator-api-key").build());
                FirebaseAuth.getInstance().useEmulator("10.0.2.2", Integer.parseInt(args.getString("authPort", "19099")));
                FirebaseAuth.getInstance().signOut();
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
                db.useEmulator("10.0.2.2", Integer.parseInt(args.getString("firestorePort", "18080")));
            });
            }
            if (role.equals("ocr-cleanup")) {
                check(configured && FirebaseAuth.getInstance().getCurrentUser() != null, "own interrupted OCR account required");
                try {
                    com.google.android.gms.tasks.Tasks.await(main(() -> com.kwmobile.hometiming.data.auth.AuthRepository.getInstance()
                            .deleteAccount(PASSWORD)), 90, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException deletion) {
                    try {
                        com.google.android.gms.tasks.Tasks.await(FirebaseAuth.getInstance().getCurrentUser().reload(), 30, java.util.concurrent.TimeUnit.SECONDS);
                        throw deletion;
                    } catch (java.util.concurrent.ExecutionException reload) {
                        if (reload.getCause() instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException
                                && ((com.google.firebase.auth.FirebaseAuthInvalidUserException) reload.getCause()).getErrorCode().equals("ERROR_USER_NOT_FOUND")) {
                            FirebaseAuth.getInstance().signOut();
                        } else throw deletion;
                    }
                }
                check(FirebaseAuth.getInstance().getCurrentUser() == null, "interrupted OCR auth cleanup complete");
                result.putString("stream", "MVP ocr-cleanup PASS (own configured test account and house)\n");
                finish(Activity.RESULT_OK, result); return;
            }
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            live = main(() -> new ViewModelProvider(activity).get(MvpViewModel.class));
            ui = main(() -> new ViewModelProvider(activity).get(DesignPreviewViewModel.class));
            if (role.equals("owner")) owner(); else if (role.equals("member")) member();
            else if (role.equals("ocr")) ocr();
            else throw new AssertionError("Unknown role");
            result.putString("stream", "MVP " + role + " PASS (" + (configured ? "configured Firebase project" : "demo Firebase emulator") + "; Android emulator)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            if (role != null && role.equals("ocr") && live != null
                    && FirebaseAuth.getInstance().getCurrentUser() != null
                    && (run + "-ocr@example.test").equals(FirebaseAuth.getInstance().getCurrentUser().getEmail())) {
                try {
                    mainAction(() -> live.deleteAccount(PASSWORD));
                    await(() -> FirebaseAuth.getInstance().getCurrentUser() == null, "failed OCR test cleanup");
                } catch (Exception cleanup) { android.util.Log.e("MvpFlowTest", "Own OCR test cleanup needs retry", cleanup); }
            }
            result.putString("stream", "MVP " + role + " FAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            android.util.Log.e("MvpFlowTest", "Integration failure", error);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void signup(String who) throws Exception {
        open("signup");
        await(() -> activity.findViewById(R.id.auth_name) != null, "signup form visible");
        text(R.id.auth_name, "Mvp" + who);
        text(R.id.auth_email, run + "-" + who.toLowerCase(java.util.Locale.ROOT) + "@example.test");
        text(R.id.auth_password, PASSWORD); text(R.id.auth_confirm, PASSWORD);
        click(R.id.auth_submit);
        await(() -> live.session != null && !live.readOnly() && !live.busy(), "signup/house/online");
        check(main(() -> ui.state.store.items.isEmpty()), "new house contains no fixtures");
    }

    private void owner() throws Exception {
        signup("Alice");
        final String ownerUid = main(() -> live.session.uid);
        mainAction(() -> {
            ui.edit(""); ui.state.draft.name = "MvpBrush-" + run;
            ui.state.draft.date = LocalDate.now(com.kwmobile.hometiming.domain.DateRules.ZONE).minusDays(30);
            ui.state.draft.cycle = "30";
        });
        recreate(); click(R.id.edit_submit);
        await(() -> !live.busy() && ui.state.draft == null && ui.state.store.items.size() == 1, "item form save");
        String itemId = main(() -> ui.state.store.items.get(0).id);
        check(main(() -> ui.state.route.id.equals(itemId)), "acknowledged detail route");
        com.google.android.gms.tasks.Tasks.await(main(() -> FirebaseFirestore.getInstance().disableNetwork()));
        await(live::readOnly, "offline becomes read only");
        mainAction(() -> { live.beginEdit(""); ui.edit(""); ui.state.draft.name = "OfflineDraft"; });
        recreate();
        check(main(() -> !activity.findViewById(R.id.edit_submit).isEnabled()), "offline form save disabled");
        com.google.android.gms.tasks.Tasks.await(main(() -> FirebaseFirestore.getInstance().enableNetwork()));
        await(() -> !live.readOnly(), "online recovers from cache");
        check(main(() -> ui.state.draft.name.equals("OfflineDraft")), "offline draft retained");
        mainAction(() -> ui.state.draft = null);
        open("detail", itemId);
        clickText(R.string.preview_copy_110);
        await(() -> !live.busy() && ui.state.store.find(itemId).completedOn != null, "item completion");
        await(() -> !ui.state.store.find(itemId).history.isEmpty(), "completion history subscription");
        check(main(() -> ui.state.store.find(itemId).history.get(0).person.equals("MvpAlice")), "completion actor name");
        // Keep the exact old write snapshot while another device edits, to exercise revision rejection.
        PreviewStore.Item beforeMember = main(() -> ui.state.store.find(itemId));
        mainAction(live::issueInvite);
        await(() -> !live.busy() && !live.inviteCode().isEmpty(), "invite issue");
        Bundle invite = new Bundle(); invite.putString("invite", main(live::inviteCode)); sendStatus(1, invite);
        await(() -> live.house != null && live.house.memberCount == 2, "second device join", 180000);
        java.util.concurrent.atomic.AtomicBoolean leftOnce = new java.util.concurrent.atomic.AtomicBoolean();
        androidx.lifecycle.Observer<Integer> membershipObserver = ignored -> {
            if (live.house != null && live.house.memberCount == 1) leftOnce.set(true);
        };
        mainAction(() -> live.changes().observeForever(membershipObserver));
        await(() -> ui.state.store.find(itemId).name.equals("MvpEdited-" + run), "second device live item update", 180000);
        mainAction(() -> live.beginEdit(itemId));
        mainAction(() -> live.complete(beforeMember, PreviewStore.today()));
        await(() -> !live.busy(), "stale completion rejected");
        check(main(() -> ui.state.store.find(itemId).serverItem.occurrenceId.equals(beforeMember.serverItem.occurrenceId)), "stale completion did not advance occurrence");
        // Bob adds the chore. Its original Friday must survive moving just this occurrence.
        await(() -> ui.state.store.items.stream().anyMatch(row -> row.chore), "second device chore", 180000);
        String choreId = main(() -> ui.state.store.items.stream().filter(row -> row.chore).findFirst().get().id);
        LocalDate original = main(() -> ui.state.store.find(choreId).plannedDue);
        mainAction(() -> live.proposal(ui.state.store.find(choreId), PreviewStore.today().plusDays(1)));
        await(() -> !live.busy() && ui.state.store.find(choreId).due.equals(PreviewStore.today().plusDays(1)), "weather occurrence move");
        check(main(() -> ui.state.store.find(choreId).plannedDue.equals(original)), "original week preserved");
        open("detail", choreId);
        await(() -> !ui.state.store.find(choreId).history.isEmpty(), "chore reschedule log");
        clickText(R.string.preview_copy_109);
        await(() -> !live.busy() && ui.state.store.find(choreId).plannedDue.equals(original.plusDays(7)), "weekly next round");
        // Personal settings and actual OS permission state are separate.
        mainAction(() -> live.settings("10:15", 1));
        await(() -> !live.busy() && live.notifyTime().equals("10:15") && live.advanceDays() == 1, "personal reminder settings");
        mainAction(() -> live.region(com.kwmobile.hometiming.data.weather.Region.of("서울특별시 마포구", 59, 127)));
        await(() -> !live.busy() && live.house.hasRegion(), "shared region");
        open("family"); check(main(() -> activity.findViewById(R.id.family_content) != null), "family screen");
        open("preferences", "notifications"); check(main(() -> activity.findViewById(R.id.preferences_content) != null), "notification screen");
        open("settings"); recreate();
        check(main(() -> live.session.uid.equals(ownerUid)), "rotation retains session");
        await(leftOnce::get, "member leaves", 180000);
        await(() -> ui.state.store.find(itemId).name.equals("MvpRejoined-" + run), "member rejoins", 180000);
        String memberUid = main(() -> live.members.stream().filter(member -> !member.uid.equals(ownerUid)).findFirst().get().uid);
        mainAction(() -> live.removeMember(memberUid));
        await(() -> !live.busy() && live.house.memberCount == 1, "owner removes member");
        mainAction(() -> live.changes().removeObserver(membershipObserver));
        mainAction(() -> live.delete(ui.state.store.find(choreId)));
        await(() -> !live.busy() && ui.state.store.find(choreId) == null, "chore delete");
        mainAction(() -> live.signOut());
        check(main(() -> ui.state.route.screen.equals("login") && ui.state.store.items.isEmpty()), "logout clears household rows");
        mainAction(() -> live.authenticate("login", "", run + "-alice@example.test", PASSWORD));
        await(() -> live.session != null && !live.readOnly() && !live.busy(), "real sign in");
        check(main(() -> ui.state.store.find(itemId) != null), "item persists across sign out");
        mainAction(() -> live.deleteAccount(PASSWORD));
        await(() -> live.session == null && !live.busy() && ui.state.store.items.isEmpty(), "owner account cleanup");
    }

    private void member() throws Exception {
        signup("Bob");
        String code = args.getString("invite", "");
        if (!code.matches("[0-9]{6}")) throw new AssertionError("Owner demo invite required");
        mainAction(() -> live.join(code));
        await(() -> live.session != null && !live.readOnly() && !live.busy() && live.house.memberCount == 2,
                "join shared house");
        String itemId = main(() -> ui.state.store.items.get(0).id);
        mainAction(() -> { live.beginEdit(itemId); ui.edit(itemId); ui.state.draft.name = "MvpEdited-" + run; ui.state.draft.cycle = "31"; });
        recreate(); click(R.id.edit_submit);
        await(() -> !live.busy() && ui.state.draft == null && ui.state.store.find(itemId).name.equals("MvpEdited-" + run), "shared item form edit");
        mainAction(() -> {
            live.beginEdit(""); ui.edit(""); ui.state.draft.chore = true;
            ui.state.draft.name = "MvpLaundry-" + run; ui.state.draft.category = "세탁";
            ui.state.draft.weather = true; ui.state.draft.weekday = 5;
            ui.state.draft.date = PreviewStore.today().minusDays(7);
        });
        recreate(); click(R.id.edit_submit);
        await(() -> !live.busy() && ui.state.draft == null && ui.state.store.items.size() == 2, "chore form save");
        String choreId = main(() -> ui.state.store.items.stream().filter(row -> row.chore).findFirst().get().id);
        await(() -> ui.state.store.find(choreId).completedOn != null, "other device chore completion", 180000);
        await(() -> live.house.hasRegion(), "shared region on second device", 180000);
        mainAction(live::leave);
        await(() -> live.session != null && !live.readOnly() && !live.busy()
                && live.house.memberCount == 1 && ui.state.store.items.isEmpty(), "leave creates empty solo house");
        mainAction(() -> live.join(code));
        await(() -> live.session != null && !live.readOnly() && !live.busy() && live.house.memberCount == 2,
                "rejoin after leave");
        mainAction(() -> { live.beginEdit(itemId); ui.edit(itemId); ui.state.draft.name = "MvpRejoined-" + run; });
        recreate(); click(R.id.edit_submit);
        await(() -> !live.busy() && ui.state.draft == null, "rejoined member ready");
        await(() -> live.session != null && !live.readOnly() && !live.busy() && live.house.memberCount == 1
                && ui.state.store.items.isEmpty(), "removed member recovers solo house", 180000);
        mainAction(() -> live.deleteAccount(PASSWORD));
        await(() -> live.session == null && !live.busy(), "member account cleanup");
    }

    /** Runs the real release editor and save flow; only candidate results use a synthetic bitmap. */
    private void ocr() throws Exception {
        signup("Ocr");
        mainAction(() -> { live.beginEdit(""); ui.edit(""); ui.state.draft.name = "Original name";
            ui.state.draft.date = LocalDate.now(com.kwmobile.hometiming.domain.DateRules.ZONE).minusDays(2);
            ui.state.draft.cycle = "45"; });
        recreate();
        check(main(() -> activity.findViewById(R.id.editor_photo_name).getVisibility() == android.view.View.VISIBLE), "release item OCR entry visible");
        LocalDate date = main(() -> ui.state.draft.date);
        String cycle = main(() -> ui.state.draft.cycle);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(1200, 900, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(getTargetContext(), R.font.pretendard_regular));
        paint.setTextSize(100); canvas.drawText("샤워 필터", 280, 300, paint);
        paint.setTextSize(75); canvas.drawText("SHOWER FILTER", 260, 450, paint);
        java.util.List<String> candidates;
        try (com.kwmobile.hometiming.data.recognition.ProductTextRecognizer recognizer = new com.kwmobile.hometiming.data.recognition.ProductTextRecognizer()) {
            candidates = com.google.android.gms.tasks.Tasks.await(recognizer.recognize(bitmap, 0), 30, java.util.concurrent.TimeUnit.SECONDS);
        }
        check(candidates.stream().anyMatch(x -> x.contains("필터")), "bundled Korean result");
        check(candidates.stream().anyMatch(x -> x.toUpperCase(java.util.Locale.ROOT).contains("FILTER")), "bundled Latin result");
        com.kwmobile.hometiming.ui.recognition.PhotoNameActivity photo = openPhoto();
        if ("true".equals(args.getString("cameraDenied"))) {
            await(() -> photo.findViewById(R.id.ocr_status) instanceof android.widget.TextView
                    && ((android.widget.TextView) photo.findViewById(R.id.ocr_status)).getText().toString()
                    .equals(photo.getString(R.string.ocr_permission_denied)), "camera permission denial");
            mainAction(() -> photo.findViewById(R.id.ocr_cancel).performClick()); waitForIdleSync();
            check(main(() -> ui.state.draft.name.equals("Original name") && ui.state.draft.date.equals(date)
                    && ui.state.draft.cycle.equals(cycle)), "permission denied manual return preserves draft");
        } else {
            await(() -> photo.findViewById(R.id.ocr_shutter).isEnabled(), "camera ready");
            com.kwmobile.hometiming.ui.recognition.PhotoNameActivity rotated = recreatePhoto(photo);
            await(() -> rotated.findViewById(R.id.ocr_shutter).isEnabled(), "camera ready after recreation");
            com.kwmobile.hometiming.ui.recognition.PhotoNameViewModel model = main(() -> new ViewModelProvider(rotated)
                    .get(com.kwmobile.hometiming.ui.recognition.PhotoNameViewModel.class));
            mainAction(() -> rotated.findViewById(R.id.ocr_shutter).performClick());
            await(() -> !model.busy, "camera image recognition completes");
            check(main(() -> model.result || model.message == R.string.ocr_empty), "camera image reaches OCR without capture/conversion error");
            mainAction(model::ready);
            mainAction(() -> { int old = model.begin(); model.cancelProcessing(); int current = model.begin();
                check(!model.current(old) && model.current(current), "late OCR result rejected"); });
            await(() -> !model.busy && model.message == R.string.ocr_timeout, "eight-second timeout recovery");
            mainAction(() -> { model.result = true; model.candidates = candidates; model.message = R.string.ocr_choose;
                model.select(candidates.get(0)); });
            check(main(() -> ui.state.draft.name.equals("Original name")), "candidate selection does not apply automatically");
            com.kwmobile.hometiming.ui.recognition.PhotoNameActivity confirmed = recreatePhoto(rotated);
            check(main(() -> ((EditText) confirmed.findViewById(R.id.ocr_name)).getText().toString().equals(candidates.get(0))),
                    "candidate survives recreation");
            mainAction(() -> confirmed.findViewById(R.id.ocr_apply).performClick()); waitForIdleSync();
            await(() -> ui.state.draft != null && ui.state.draft.name.equals(candidates.get(0)), "OCR result delivered to editor");
            check(main(() -> ui.state.draft.date.equals(date) && ui.state.draft.cycle.equals(cycle)), "explicit name apply preserves date and cycle");
            com.kwmobile.hometiming.ui.recognition.PhotoNameActivity canceled = openPhoto();
            mainAction(() -> canceled.findViewById(R.id.ocr_cancel).performClick()); waitForIdleSync();
            check(main(() -> ui.state.draft.name.equals(candidates.get(0))), "cancel preserves existing name");
            click(R.id.edit_submit);
            await(() -> !live.busy() && ui.state.draft == null && ui.state.store.items.size() == 1, "OCR item saved to Firebase");
            await(() -> ui.state.store.items.get(0).name.equals(candidates.get(0)), "saved OCR name");
            mainAction(() -> { live.beginEdit(""); ui.edit(""); }); recreate();
            com.kwmobile.hometiming.ui.recognition.PhotoNameActivity stale = openPhoto();
            mainAction(() -> { ui.state.draft = new DesignPreviewViewModel.Draft(); ui.state.draft.name = "New draft";
                var staleModel = new ViewModelProvider(stale).get(com.kwmobile.hometiming.ui.recognition.PhotoNameViewModel.class);
                staleModel.result = true; staleModel.candidates = candidates; staleModel.select(candidates.get(0));
                stale.findViewById(R.id.ocr_apply).performClick(); }); waitForIdleSync();
            check(main(() -> ui.state.draft.name.equals("New draft")), "stale OCR cannot overwrite a new draft");
        }
        mainAction(() -> { live.beginEdit(""); ui.edit(""); ui.state.draft.chore = true; }); recreate();
        check(main(() -> activity.findViewById(R.id.editor_photo_name).getVisibility() == android.view.View.GONE), "chore has no OCR entry");
        mainAction(() -> live.deleteAccount(PASSWORD));
        await(() -> FirebaseAuth.getInstance().getCurrentUser() == null, "OCR account and house cleanup");
    }
    private com.kwmobile.hometiming.ui.recognition.PhotoNameActivity openPhoto() {
        ActivityMonitor monitor = addMonitor(com.kwmobile.hometiming.ui.recognition.PhotoNameActivity.class.getName(), null, false);
        click(R.id.editor_photo_name);
        var photo = (com.kwmobile.hometiming.ui.recognition.PhotoNameActivity) waitForMonitorWithTimeout(monitor, 15000);
        removeMonitor(monitor); check(photo != null, "photo activity opened"); waitForIdleSync(); return photo;
    }
    private com.kwmobile.hometiming.ui.recognition.PhotoNameActivity recreatePhoto(com.kwmobile.hometiming.ui.recognition.PhotoNameActivity previous) throws Exception {
        AtomicReference<com.kwmobile.hometiming.ui.recognition.PhotoNameActivity> next = new AtomicReference<>();
        Application app = (Application) getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks callback = new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) { if (a instanceof com.kwmobile.hometiming.ui.recognition.PhotoNameActivity) next.set((com.kwmobile.hometiming.ui.recognition.PhotoNameActivity) a); }
            public void onActivityStarted(Activity a) { }
            public void onActivityResumed(Activity a) { }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            public void onActivityDestroyed(Activity a) { }
        };
        app.registerActivityLifecycleCallbacks(callback);
        try {
            mainAction(previous::recreate);
            await(() -> next.get() != null && next.get().findViewById(R.id.ocr_root) != null, "photo activity recreation");
            waitForIdleSync(); return next.get();
        } finally { app.unregisterActivityLifecycleCallbacks(callback); }
    }

    private void open(String page) throws Exception { open(page, ""); }
    private void open(String page, String id) throws Exception {
        mainAction(() -> { ui.state.route = new DesignPreviewViewModel.Route(page, id); ui.state.back.clear(); });
        recreate();
    }
    private void recreate() throws Exception {
        MainActivity previous = activity;
        AtomicReference<MainActivity> replacement = new AtomicReference<>();
        Application app = (Application) getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks callback = new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) { if (a instanceof MainActivity) replacement.set((MainActivity) a); }
            public void onActivityStarted(Activity a) { }
            public void onActivityResumed(Activity a) { }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            public void onActivityDestroyed(Activity a) { }
        };
        app.registerActivityLifecycleCallbacks(callback);
        mainAction(previous::recreate);
        await(() -> replacement.get() != null && replacement.get().findViewById(R.id.screen_container) != null, "activity recreate");
        activity = replacement.get();
        waitForIdleSync(); app.unregisterActivityLifecycleCallbacks(callback);
    }
    private void text(int id, String value) { mainAction(() -> ((EditText) activity.findViewById(id)).setText(value)); }
    private void clickText(int textId) {
        mainAction(() -> {
            android.view.View view = findButton(activity.getWindow().getDecorView(), activity.getString(textId));
            check(view != null && view.isEnabled(), "enabled screen action: " + activity.getString(textId));
            view.performClick();
        });
    }
    private android.view.View findButton(android.view.View view, String text) {
        if (view instanceof android.widget.Button && text.contentEquals(((android.widget.Button) view).getText())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View result = findButton(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }
    private void click(int id) { mainAction(() -> {
        android.view.View view = activity.findViewById(id);
        check(view != null && view.isEnabled(), "enabled action " + id); view.performClick();
    }); }
    private void mainAction(Runnable action) { runOnMainSync(action); }
    private <T> T main(Supplier<T> read) {
        AtomicReference<T> result = new AtomicReference<>(); runOnMainSync(() -> result.set(read.get())); return result.get();
    }
    private void await(BooleanSupplier condition, String description) throws Exception { await(condition, description, 45000); }
    private void await(BooleanSupplier condition, String description, long timeout) throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + timeout;
        while (!main(condition::getAsBoolean)) {
            if (android.os.SystemClock.elapsedRealtime() >= end) throw new AssertionError("Timed out: " + description);
            Thread.sleep(100);
        }
        Bundle evidence = new Bundle(); evidence.putString("step", role + ": " + description); sendStatus(0, evidence);
    }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
}
