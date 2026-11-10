package com.kwmobile.hometiming.ui.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kwmobile.hometiming.R;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import java.util.function.Consumer;

/** Optional product metadata. Owns rendering, never a draft or persistence layer. */
public final class OptionalItemFieldsView extends LinearLayout {
    public OptionalItemFieldsView(Context context) { this(context,null); }
    public OptionalItemFieldsView(Context context,AttributeSet attrs) {
        super(context,attrs);setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.component_optional_item_fields,this,true);
    }
    public void bind(String product,String location,String memo,boolean expanded,
                     Consumer<String> productChanged,Consumer<String> locationChanged,
                     Consumer<String> memoChanged,Consumer<Boolean> expandedChanged) {
        field(R.id.edit_product_name,product,productChanged);
        field(R.id.edit_location,location,locationChanged);
        field(R.id.edit_memo,memo,memoChanged);
        showExpanded(expanded);
        Runnable summary=() -> ((TextView)findViewById(R.id.optional_summary)).setText(
                hasText()?R.string.component_optional_filled:R.string.component_optional_empty);
        summary.run();
        findViewById(R.id.optional_toggle).setOnClickListener(v -> {
            boolean next=findViewById(R.id.optional_fields).getVisibility()!=VISIBLE;
            showExpanded(next);summary.run();expandedChanged.accept(next);
        });
    }
    private boolean hasText() {
        for(int id:new int[]{R.id.edit_product_name,R.id.edit_location,R.id.edit_memo})
            if(!((EditText)findViewById(id)).getText().toString().trim().isEmpty())return true;
        return false;
    }
    private void showExpanded(boolean expanded) {
        findViewById(R.id.optional_fields).setVisibility(expanded?VISIBLE:GONE);
        findViewById(R.id.optional_arrow).setRotation(expanded?90:0);
        androidx.core.view.ViewCompat.setStateDescription(findViewById(R.id.optional_toggle),
                getContext().getString(expanded?R.string.component_expand:R.string.component_collapse));
    }
    private void field(int id,String value,Consumer<String> changed) {
        EditText field=findViewById(id);
        Object previous=field.getTag();if(previous instanceof TextWatcher)field.removeTextChangedListener((TextWatcher)previous);
        field.setText(value==null?"":value);
        TextWatcher watcher=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){changed.accept(s.toString());}
            public void afterTextChanged(Editable text){}
        };
        field.addTextChangedListener(watcher);field.setTag(watcher);
    }
}
