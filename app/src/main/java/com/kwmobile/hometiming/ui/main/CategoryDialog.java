package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;
import java.util.List;
import java.util.function.Consumer;

/** Shared category input for the local preview list and editor. */
final class CategoryDialog {
    private final Context context;
    private final DesignPreviewViewModel vm;
    private final PreviewWidgets ui;

    CategoryDialog(Context context, DesignPreviewViewModel vm, PreviewWidgets ui) {
        this.context = context;
        this.vm = vm;
        this.ui = ui;
    }

    String[] defaults(boolean chore) {
        if (vm.state.live && chore) return new String[] {"세탁", "건조", "환기", "청소", "기타"};
        return new String[] {
            context.getString(chore ? R.string.preview_copy_089 : R.string.preview_copy_023),
            context.getString(chore ? R.string.preview_copy_092 : R.string.preview_copy_052),
            context.getString(R.string.preview_copy_053)
        };
    }

    void choose(boolean chore, String selected, Consumer<String> pick, Runnable changed) {
        List<String> names = vm.categories(chore, defaults(chore));
        if (!names.contains(selected)) names.add(selected);
        if (!vm.state.live || !chore) names.add(context.getString(R.string.category_add_option));
        MaterialAlertDialogBuilder chooser = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.preview_copy_091)
                .setItems(names.toArray(new String[0]), (dialog, index) -> {
                    if ((!vm.state.live || !chore) && index == names.size() - 1) add(chore, pick);
                    else pick.accept(names.get(index));
                })
                .setNegativeButton(R.string.preview_copy_288, null);
        if (!vm.state.live) chooser.setNeutralButton(R.string.category_edit,
                (dialog, which) -> editCategories(chore, changed));
        chooser.show();
    }

    private void editCategories(boolean chore, Runnable changed) {
        List<String> names = vm.categories(chore, defaults(chore));
        LinearLayout body = ui.vertical();
        body.setPadding(ui.dp(24), 0, ui.dp(24), 0);
        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = ui.vertical();
        scroll.addView(rows);
        int maxHeight = Math.min(ui.dp(320),
                context.getResources().getDisplayMetrics().heightPixels / 2);
        body.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(ui.dp(names.size() * 64), maxHeight)));
        AlertDialog editor = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.category_edit_title)
                .setView(body)
                .setNegativeButton(R.string.category_done, null)
                .create();
        Runnable refresh = () -> {
            editor.dismiss();
            changed.run();
            editCategories(chore, changed);
        };
        for (String name : names) {
            LinearLayout row = ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(ui.dp(64));
            TextView label = ui.label(name);
            label.setMaxLines(3);
            label.setMinHeight(ui.dp(48));
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setFocusable(true);
            label.setContentDescription(context.getString(R.string.category_rename_for, name));
            android.util.TypedValue feedback = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, feedback, true);
            label.setBackgroundResource(feedback.resourceId);
            label.setOnClickListener(view -> editName(chore, name, value -> refresh.run()));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton delete = ui.smallAction(context.getString(R.string.category_delete),
                    () -> deleteCategory(chore, name, refresh));
            delete.setContentDescription(context.getString(R.string.category_delete_for, name));
            delete.setPadding(ui.dp(8), 0, ui.dp(8), 0);
            LinearLayout.LayoutParams deleteSize = new LinearLayout.LayoutParams(-2, ui.dp(48));
            deleteSize.leftMargin = ui.dp(4);
            row.addView(delete, deleteSize);
            rows.addView(row);
        }
        editor.show();
    }

    private void deleteCategory(boolean chore, String name, Runnable changed) {
        List<String> targets = vm.categories(chore, defaults(chore));
        targets.remove(name);
        if (targets.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.category_delete_title)
                    .setMessage(R.string.category_delete_last)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else if (vm.categoryInUse(chore, name)) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.category_move_title)
                    .setItems(targets.toArray(new String[0]), (dialog, index) ->
                            confirmDelete(chore, name, targets.get(index), changed))
                    .setNegativeButton(R.string.preview_copy_288, null)
                    .show();
        } else {
            confirmDelete(chore, name, null, changed);
        }
    }

    private void confirmDelete(
            boolean chore, String name, String replacement, Runnable changed) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.category_delete_title)
                .setMessage(replacement == null
                        ? context.getString(R.string.category_delete_message, name)
                        : context.getString(R.string.category_delete_move_message, name, replacement))
                .setNegativeButton(R.string.preview_copy_288, null)
                .setPositiveButton(R.string.category_delete, (dialog, which) -> {
                    if (vm.deleteCategory(chore, name, replacement, defaults(chore),
                            context.getString(R.string.preview_copy_051))) {
                        changed.run();
                    } else {
                        new MaterialAlertDialogBuilder(context)
                                .setMessage(R.string.category_edit_changed)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                })
                .show();
    }

    void add(boolean chore, Consumer<String> added) {
        editName(chore, null, added);
    }

    private void editName(boolean chore, String oldName, Consumer<String> saved) {
        LinearLayout body = ui.vertical();
        body.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        EditText input = new androidx.appcompat.widget.AppCompatEditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.category_name_hint);
        input.setMinHeight(ui.dp(48));
        if (oldName != null) {
            input.setText(oldName);
            input.selectAll();
        }
        body.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(oldName == null ? R.string.category_add_title : R.string.category_rename)
                .setView(body)
                .setNegativeButton(R.string.preview_copy_288, null)
                .setPositiveButton(oldName == null ? R.string.category_add_action : R.string.category_save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = input.getText().toString().trim();
                String allLabel = context.getString(R.string.preview_copy_051);
                boolean success = oldName == null
                        ? vm.addCategory(chore, name, defaults(chore), allLabel)
                        : vm.renameCategory(chore, oldName, name, defaults(chore), allLabel);
                if (!success) {
                    boolean duplicate = vm.categories(chore, defaults(chore)).contains(name);
                    input.setError(context.getString(duplicate
                            ? R.string.category_duplicate : R.string.category_invalid));
                    return;
                }
                dialog.dismiss();
                saved.accept(name);
            });
            input.requestFocus();
            if (dialog.getWindow() != null)
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }
}
