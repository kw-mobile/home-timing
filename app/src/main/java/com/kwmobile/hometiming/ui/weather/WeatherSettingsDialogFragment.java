package com.kwmobile.hometiming.ui.weather;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.ui.main.WeatherViewModel;

/** Uses the host's weather state; toggling remains a local, explicitly labelled test preference. */
public final class WeatherSettingsDialogFragment extends DialogFragment {
    public static final String TAG = "weather-settings";
    private WeatherViewModel vm;
    private View content;
    private boolean binding;
    public static void open(FragmentManager manager) {
        if (!manager.isStateSaved() && manager.findFragmentByTag(TAG) == null)
            new WeatherSettingsDialogFragment().showNow(manager, TAG);
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        vm = new ViewModelProvider(requireActivity()).get(WeatherViewModel.class);
        content = getLayoutInflater().inflate(R.layout.dialog_weather_settings, new android.widget.FrameLayout(requireContext()), false);
        ((SwitchMaterial)content.findViewById(R.id.weather_test_toggle)).setOnCheckedChangeListener((button, checked) -> {
            if (!binding) vm.setTestMode(checked);
        });
        vm.state().observe(this, state -> render());
        render();
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.weather_ui_mode)
                .setView(content).setNegativeButton(R.string.settings_dialog_close, null)
                .setNeutralButton(R.string.weather_ui_retry, null).create();
    }
    @Override public void onStart() {
        super.onStart();
        ((AlertDialog)requireDialog()).getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> vm.refresh(true));
        render();
    }
    @Override public void onDestroyView() { content = null; super.onDestroyView(); }
    private void render() {
        if (content == null) return;
        binding = true;
        ((SwitchMaterial)content.findViewById(R.id.weather_test_toggle)).setChecked(vm.isTestMode());
        binding = false;
        ((TextView)content.findViewById(R.id.weather_mode_status)).setText(vm.isTestMode()
                ? R.string.weather_ui_source_test : R.string.weather_ui_real);
        AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog != null && dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(vm.current() == null || !vm.current().loading);
    }
}
