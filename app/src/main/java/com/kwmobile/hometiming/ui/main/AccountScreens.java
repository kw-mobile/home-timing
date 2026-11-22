package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;
import com.kwmobile.hometiming.R;

/** Binds local fixture screens without owning lifecycle or saved state. */
final class AccountScreens {
    private final Context context;
    private final View screen;
    private final DesignPreviewViewModel vm;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;

    AccountScreens(View root, DesignPreviewViewModel vm, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext();
        this.screen = root;
        this.vm = vm;
        this.preview = vm.state;
        this.host = host;
        this.ui = ui;
    }

    void bindAuth(String mode) {
        android.widget.TextView footer = screen.findViewById(R.id.auth_footer);
        if (host.mvp() != null && footer != null) {
            footer.setText(R.string.mvp_auth_help);
            footer.setVisibility(mode.equals("login") ? View.GONE : View.VISIBLE);
        }
        int[] ids = {R.id.auth_email, R.id.auth_password, R.id.auth_name, R.id.auth_confirm};
        for (int id : ids) {
            EditText field = screen.findViewById(id);
            if (field == null) continue;
            String key = mode + ":" + id;
            field.setText(preview.authFields.getOrDefault(key, ""));
            field.addTextChangedListener(ui.watcher(value -> preview.authFields.put(key, value)));
        }
        EditText password = screen.findViewById(R.id.auth_password);
        if (password != null && mode.equals("login")) {
            LinearLayout row = ui.horizontal();
            row.setGravity(android.view.Gravity.END);
            MaterialButton reset =
                    ui.smallAction(
                            context.getString(R.string.preview_copy_072),
                            () -> host.go("reset"));
            reset.setBackgroundTintList(
                    ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            row.addView(reset, new LinearLayout.LayoutParams(-2, -2));
            ui.box(R.id.auth_options).addView(row);
        }
        MaterialButton submit = screen.findViewById(R.id.auth_submit);
        if (submit == null) return;
        EditText lastField = mode.equals("signup")
                ? screen.findViewById(R.id.auth_confirm)
                : (mode.equals("reset") ? screen.findViewById(R.id.auth_email) : screen.findViewById(R.id.auth_password));
        if (lastField != null) {
            lastField.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO
                        || (mode.equals("login") && actionId == EditorInfo.IME_ACTION_NEXT)
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    submit.performClick();
                    return true;
                }
                return false;
            });
        }
        View change = screen.findViewById(R.id.auth_switch);
        if (change != null)
            change.setOnClickListener(v -> host.go(mode.equals("login") ? "signup" : "login"));
        if (host.mvp() != null) {
            submit.setEnabled(!host.mvp().busy());
            if (host.mvp().busy()) submit.setText(R.string.mvp_saving);
            if (mode.equals("login") && host.mvp().hasStoredAccount() && !host.mvp().busy()) {
                LinearLayout options = ui.box(R.id.auth_options);
                options.addView(ui.caption(context.getString(R.string.ui_session_recovery)));
                ui.fullAction(options, context.getString(R.string.mvp_retry_session), false, () -> host.mvp().restore());
                ui.fullAction(options,
                        context.getString(R.string.mvp_delete_account), false,
                        () -> MvpHouseholdScreens.deleteAccount(context, host, ui));
            }
        }
        submit.setOnClickListener(
                v -> {
                    boolean valid = validateEmail();
                    if (password != null && password.getText().toString().length() < 6) {
                        ui.fieldError(
                                R.id.auth_password_error,
                                context.getString(R.string.preview_copy_073));
                        valid = false;
                    } else ui.clearError(R.id.auth_password_error);
                    if (mode.equals("signup")) {
                        EditText name = screen.findViewById(R.id.auth_name);
                        EditText repeat = screen.findViewById(R.id.auth_confirm);
                        if (name.getText().toString().trim().isEmpty()) {
                            ui.fieldError(
                                    R.id.auth_name_error,
                                    context.getString(R.string.preview_copy_074));
                            valid = false;
                        } else ui.clearError(R.id.auth_name_error);
                        if (!repeat.getText().toString().equals(password.getText().toString())) {
                            ui.fieldError(
                                    R.id.auth_confirm_error,
                                    context.getString(R.string.preview_copy_075));
                            valid = false;
                        } else ui.clearError(R.id.auth_confirm_error);
                    }
                    if (!valid) {
                        ui.focusFirstError();
                        return;
                    }
                    host.hideKeyboard();
                    if (host.mvp() != null) {
                        String name = mode.equals("signup") ? ((EditText) screen.findViewById(R.id.auth_name)).getText().toString() : "";
                        host.mvp().authenticate(mode, name,
                                ((EditText) screen.findViewById(R.id.auth_email)).getText().toString(),
                                password == null ? "" : password.getText().toString());
                        return;
                    }
                    submit.setEnabled(false);
                    submit.setText(context.getString(R.string.preview_copy_076));
                    String key = preview.route.key();
                    View current = screen;
                    current.postDelayed(
                            () -> {
                                if (!host.isCurrent(current, key)) return;
                                submit.setEnabled(true);
                                if (preview.scenario.equals("error")
                                        || preview.scenario.equals("offline")) {
                                    submit.setText(
                                            mode.equals("reset")
                                                    ? context.getString(R.string.preview_copy_031)
                                                    : mode.equals("signup")
                                                            ? context.getString(
                                                                    R.string.preview_copy_077)
                                                            : context.getString(
                                                                    R.string.preview_copy_005));
                                    host.notice(context.getString(R.string.preview_copy_078));
                                    return;
                                }
                                if (mode.equals("reset")) {
                                    submit.setText(context.getString(R.string.preview_copy_079));
                                    LinearLayout feedback = ui.box(R.id.reset_feedback);
                                    feedback.removeAllViews();
                                    ui.gap(feedback, 24);
                                    ui.section(
                                            feedback,
                                            context.getString(R.string.preview_copy_080),
                                            context.getString(R.string.preview_copy_081));
                                    ui.fullAction(
                                            feedback,
                                            context.getString(R.string.preview_copy_082),
                                            false,
                                            () -> {
                                                vm.tab("login");
                                                host.render();
                                            });
                                } else {
                                    preview.authFields.clear();
                                    preview.passwordVisible = false;
                                    vm.tab("home");
                                    host.render();
                                    host.notice(
                                            mode.equals("signup")
                                                    ? context.getString(R.string.preview_copy_083)
                                                    : context.getString(R.string.preview_copy_084));
                                }
                            },
                            450);
                });
    }

    private boolean validateEmail() {
        EditText email = screen.findViewById(R.id.auth_email);
        boolean valid =
                android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email.getText().toString().trim())
                        .matches();
        if (!valid)
            ui.fieldError(R.id.auth_email_error, context.getString(R.string.preview_copy_085));
        else ui.clearError(R.id.auth_email_error);
        return valid;
    }
}
