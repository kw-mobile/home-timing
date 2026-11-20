package com.kwmobile.hometiming.ui.calendar;

import android.Manifest;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;

/** A short device preference, kept above settings instead of adding a navigation destination. */
public final class CalendarSettingsDialogFragment extends DialogFragment {
    public static final String TAG = "calendar-settings";
    private CalendarSettingsViewModel vm;
    private View content;
    private boolean chooseAfterPermission;
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                chooseAfterPermission = vm.manager.permitted();
                vm.refresh();
            });

    public static void open(FragmentManager manager) {
        if (!manager.isStateSaved() && manager.findFragmentByTag(TAG) == null)
            new CalendarSettingsDialogFragment().showNow(manager, TAG);
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        vm = new ViewModelProvider(this).get(CalendarSettingsViewModel.class);
        chooseAfterPermission = saved != null && saved.getBoolean("choose_after_permission");
        content = getLayoutInflater().inflate(R.layout.dialog_calendar_settings, new android.widget.FrameLayout(requireContext()), false);
        content.findViewById(R.id.calendar_choose).setOnClickListener(view -> {
            if (!vm.manager.permitted()) {
                chooseAfterPermission = true;
                permissions.launch(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR});
            } else if (!vm.choices.isEmpty()) openAction(ActionDialog.PICKER);
            else vm.refresh();
        });
        content.findViewById(R.id.calendar_sync).setOnClickListener(view -> vm.sync());
        content.findViewById(R.id.calendar_disable).setOnClickListener(view -> openAction(ActionDialog.DISABLE));
        content.findViewById(R.id.calendar_details).setOnClickListener(view -> openAction(ActionDialog.DETAILS));
        vm.changes().observe(this, ignored -> render());
        render();
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.calendar_title)
                .setView(content).setNegativeButton(R.string.settings_dialog_close, null).create();
    }

    @Override public void onResume() {
        super.onResume();
        vm.refresh();
        vm.manager.sync();
        render();
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        out.putBoolean("choose_after_permission", chooseAfterPermission);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() { content = null; super.onDestroyView(); }

    private void openAction(String kind) {
        FragmentManager children = getChildFragmentManager();
        if (!children.isStateSaved() && children.findFragmentByTag("calendar-action") == null) {
            ActionDialog dialog = new ActionDialog();
            Bundle args = new Bundle(); args.putString("kind", kind); dialog.setArguments(args);
            dialog.show(children, "calendar-action");
        }
    }
    private void render() {
        if (content == null) return;
        String status = getString(!vm.manager.permitted() ? R.string.calendar_permission_title :
                vm.manager.enabled() ? R.string.calendar_enabled : R.string.calendar_off);
        java.util.List<String> details = new java.util.ArrayList<>();
        if (vm.manager.enabled()) details.add(vm.manager.label());
        if (!vm.manager.permitted()) details.add(getString(R.string.calendar_permission_hint));
        else if (vm.choices.isEmpty()) details.add(getString(R.string.calendar_empty));
        if (vm.manager.cleanupPending()) details.add(getString(R.string.calendar_cleanup_pending));
        if (vm.message != 0) details.add(getString(vm.message));
        ((TextView)content.findViewById(R.id.calendar_status)).setText(vm.busy ? getString(R.string.calendar_busy) : status);
        TextView detail = content.findViewById(R.id.calendar_status_detail);
        detail.setText(android.text.TextUtils.join("\n", details));
        detail.setVisibility(vm.busy || details.isEmpty() ? View.GONE : View.VISIBLE);
        content.findViewById(R.id.calendar_choose).setEnabled(!vm.busy);
        boolean hasManagedCalendar = vm.manager.enabled() || vm.manager.cleanupPending();
        content.findViewById(R.id.calendar_sync).setVisibility(hasManagedCalendar ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.calendar_sync).setEnabled(!vm.busy && vm.manager.permitted());
        ((TextView)content.findViewById(R.id.calendar_sync)).setText(vm.manager.cleanupPending()
                ? R.string.calendar_cleanup_action : R.string.calendar_sync);
        content.findViewById(R.id.calendar_disable).setVisibility(hasManagedCalendar ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.calendar_disable).setEnabled(!vm.busy);
        if (chooseAfterPermission && isResumed() && !vm.busy && !getChildFragmentManager().isStateSaved()) {
            chooseAfterPermission = false;
            if (!vm.choices.isEmpty() && vm.manager.permitted()) openAction(ActionDialog.PICKER);
        }
    }

    /** Child dialogs restore with their parent and share its operation state across rotation. */
    public static final class ActionDialog extends DialogFragment {
        static final String PICKER = "picker", DISABLE = "disable", DETAILS = "details";
        @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
            CalendarSettingsViewModel vm = new ViewModelProvider(requireParentFragment()).get(CalendarSettingsViewModel.class);
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext());
            String kind = requireArguments().getString("kind");
            if (PICKER.equals(kind)) {
                android.widget.ArrayAdapter<String> choices = new android.widget.ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_list_item_1, new java.util.ArrayList<>());
                vm.changes().observe(this, ignored -> {
                    choices.clear();
                    for (com.kwmobile.hometiming.data.calendar.DeviceCalendarStore.Choice choice : vm.choices)
                        choices.add(choice.label);
                    choices.notifyDataSetChanged();
                });
                return dialog.setTitle(R.string.calendar_choose)
                        .setAdapter(choices, (ignored, index) -> vm.enable(index))
                        .setNegativeButton(android.R.string.cancel, null).create();
            }
            if (DISABLE.equals(kind)) return dialog.setTitle(R.string.calendar_disable)
                    .setMessage(R.string.calendar_disable_help).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.calendar_disable, (ignored, which) -> vm.disable()).create();
            return dialog.setTitle(R.string.calendar_title).setMessage(getString(R.string.calendar_help) + "\n\n" + getString(R.string.calendar_permission))
                    .setPositiveButton(android.R.string.ok, null).create();
        }
    }
}
