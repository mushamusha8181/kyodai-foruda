package com.mushamusha.giantfolder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppPickerActivity extends Activity {
    private final List<AppCatalog.Entry> all = new ArrayList<>();
    private final List<AppCatalog.Entry> shown = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private AppAdapter adapter;
    private Typeface tf;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        tf = Ui.appTypeface(this);
        final boolean compact = compactUi();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,compact?7:10), Ui.dp(this,compact?7:10), Ui.dp(this,compact?7:10), Ui.dp(this,compact?6:8));
        root.setBackgroundColor(0xFF18181B);

        TextView title = text("アプリを追加", compact?20:22, Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        EditText search = new EditText(this);
        search.setHint("アプリ名を検索");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFFB7B7BF);
        search.setBackground(Ui.rounded(0xFF2B2B30, 18, this));
        search.setPadding(Ui.dp(this,compact?10:12), 0, Ui.dp(this,compact?10:12), 0);
        search.setTextSize(compact?13:14);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(this,compact?40:44));
        sp.setMargins(0, Ui.dp(this,compact?6:8),0,Ui.dp(this,compact?5:7));
        root.addView(search, sp);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button selectVisible = button("表示中を全部選択");
        Button done = button("追加する");
        actions.addView(selectVisible, new LinearLayout.LayoutParams(0, Ui.dp(this,compact?40:44), 1));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, Ui.dp(this,compact?40:44), 1);
        dp.setMargins(Ui.dp(this,6),0,0,0);
        actions.addView(done, dp);
        root.addView(actions);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setCacheColorHint(Color.TRANSPARENT);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1,0,1));
        Ui.applySafeInsets(root);
        setContentView(root);

        loadApps();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ filter(s.toString()); }
            public void afterTextChanged(Editable e){}
        });
        selectVisible.setOnClickListener(v -> {
            for (AppCatalog.Entry e : shown) selected.add(e.key());
            adapter.notifyDataSetChanged();
        });
        done.setOnClickListener(v -> finishWithResult());
    }

    private boolean compactUi(){ return getResources().getConfiguration().screenWidthDp<=360 || getResources().getConfiguration().screenHeightDp<=700; }
    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(tf); return t;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTypeface(tf); b.setTextSize(13); b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),0); return b;
    }

    private void loadApps() {
        all.clear();
        // The picker only needs launcher identity and label; skip smart-folder metadata here.
        all.addAll(AppCatalog.listLaunchable(this,false));
        filter("");
    }

    private void filter(String q) {
        shown.clear();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        for (AppCatalog.Entry e : all) {
            String label=e.label==null?"":e.label;
            String pkg=e.pkg==null?"":e.pkg;
            if (needle.isEmpty() || label.toLowerCase(Locale.ROOT).contains(needle) || pkg.toLowerCase(Locale.ROOT).contains(needle)) shown.add(e);
        }
        adapter.notifyDataSetChanged();
    }

    private void finishWithResult() {
        JSONArray arr = new JSONArray();
        for (AppCatalog.Entry e : all) if (selected.contains(e.key())) {
            JSONObject o = new JSONObject();
            try {
                o.put("label", e.label); o.put("package", e.pkg); o.put("activity", e.activity); arr.put(o);
            } catch (Exception ignored) {}
        }
        Intent data = new Intent(); data.putExtra("apps_json", arr.toString());
        setResult(RESULT_OK, data); finish();
    }

    private class AppAdapter extends BaseAdapter {
        class Holder { CheckBox cb; ImageView iv; TextView tv; }
        public int getCount(){ return shown.size(); }
        public Object getItem(int p){ return shown.get(p); }
        public long getItemId(int p){ return p; }
        public View getView(int p, View convert, ViewGroup parent) {
            AppCatalog.Entry e = shown.get(p);
            boolean compact = compactUi();
            Holder h;
            if(convert instanceof LinearLayout && convert.getTag() instanceof Holder){
                h=(Holder)convert.getTag();
            }else{
                h=new Holder();
                LinearLayout row=new LinearLayout(AppPickerActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(AppPickerActivity.this,compact?6:8),Ui.dp(AppPickerActivity.this,compact?3:5),Ui.dp(AppPickerActivity.this,compact?6:8),Ui.dp(AppPickerActivity.this,compact?3:5));
                row.setBackground(Ui.rounded(0xFF242428,16,AppPickerActivity.this));
                LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,Ui.dp(AppPickerActivity.this,compact?52:58));
                rlp.setMargins(0,Ui.dp(AppPickerActivity.this,3),0,Ui.dp(AppPickerActivity.this,3));
                row.setLayoutParams(rlp);
                CheckBox cb=new CheckBox(AppPickerActivity.this); cb.setClickable(false);
                ImageView iv=new ImageView(AppPickerActivity.this); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                TextView tv=text("",compact?14:15,Color.WHITE); tv.setPadding(Ui.dp(AppPickerActivity.this,compact?7:9),0,0,0); tv.setSingleLine(true);
                row.addView(cb,new LinearLayout.LayoutParams(Ui.dp(AppPickerActivity.this,compact?34:38),Ui.dp(AppPickerActivity.this,compact?34:38)));
                row.addView(iv,new LinearLayout.LayoutParams(Ui.dp(AppPickerActivity.this,compact?38:42),Ui.dp(AppPickerActivity.this,compact?38:42)));
                row.addView(tv,new LinearLayout.LayoutParams(0,-1,1));
                h.cb=cb;h.iv=iv;h.tv=tv;row.setTag(h);convert=row;
            }
            h.cb.setChecked(selected.contains(e.key()));
            h.tv.setText(e.label);
            h.iv.setImageDrawable(AppCatalog.icon(AppPickerActivity.this,e));
            convert.setOnClickListener(v->{
                if(selected.contains(e.key()))selected.remove(e.key()); else selected.add(e.key());
                notifyDataSetChanged();
            });
            return convert;
        }
    }
}
