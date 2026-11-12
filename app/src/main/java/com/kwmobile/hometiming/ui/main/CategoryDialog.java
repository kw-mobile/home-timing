package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
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
        return new String[] {
            context.getString(chore ? R.string.preview_copy_089 : R.string.preview_copy_023),
            context.getString(chore ? R.string.preview_copy_092 : R.string.preview_copy_052),
            context.getString(R.string.preview_copy_053)
        };
    }

    void choose(boolean chore, String selected, Consumer<String> pick) {
        List<String> names = vm.categories(chore, defaults(chore));
        if (!names.contains(selected)) names.add(selected);
        names.add(context.getString(R.string.category_add_option));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.preview_copy_091)
                .setItems(names.toArray(new String[0]), (dialog, index) -> {
                    if (index == names.size() - 1) add(chore, pick);
                    else pick.accept(names.get(index));
                })
                .setNegativeButton(R.string.preview_copy_288, null)
                .show();
    }

    void add(boolean chore, Consumer<String> added) {
        LinearLayout body = ui.vertical();
        body.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        EditText input = new androidx.appcompat.widget.AppCompatEditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.category_name_hint);
        input.setMinHeight(ui.dp(48));
        body.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.category_add_title)
                .setView(body)
                .setNegativeButton(R.string.preview_copy_288, null)
                .setPositiveButton(R.string.category_add_action, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = input.getText().toString().trim();
                if (!vm.addCategory(chore, name, defaults(chore),
                        context.getString(R.string.preview_copy_051))) {
                    boolean duplicate = vm.categories(chore, defaults(chore)).contains(name);
                    input.setError(context.getString(duplicate
                            ? R.string.category_duplicate : R.string.category_invalid));
                    return;
                }
                dialog.dismiss();
                added.accept(name);
            });
            input.requestFocus();
            if (dialog.getWindow() != null)
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }
}
