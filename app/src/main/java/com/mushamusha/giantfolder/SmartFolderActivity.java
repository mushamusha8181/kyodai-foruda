package com.mushamusha.giantfolder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SmartFolderActivity extends Activity {
    private Typeface tf;
    private String kind;
    private DataStore store;
    private boolean refreshOnNextResume=false;

    @Override protected void onResume(){
        super.onResume();
        // If the user returned with Android Back instead of the overlay, remove stale return UI here too.
        ReturnReminderManager.endSession(this);
        try{stopService(new Intent(this,OverlayService.class));}catch(Exception ignored){}
        // Recent/frequent contents and ordering can change after an app launched from this screen returns.
        if(refreshOnNextResume){refreshOnNextResume=false;recreate();}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        tf=Ui.appTypeface(this);
        store=new DataStore(this);
        kind=getIntent().getStringExtra("kind");
        String title=getIntent().getStringExtra("name");
        if(title==null||title.isEmpty()) title="スマートフォルダ";

        boolean compact=getResources().getConfiguration().screenWidthDp<=360||getResources().getConfiguration().screenHeightDp<=700;
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF18181B);
        root.setPadding(Ui.dp(this,compact?7:10),Ui.dp(this,compact?7:10),Ui.dp(this,compact?7:10),Ui.dp(this,compact?7:10));
        TextView head=text(title,compact?20:22,Color.WHITE);
        root.addView(head,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?40:44)));

        ScrollView scroll=new ScrollView(this);
        GridLayout grid=new GridLayout(this);
        int cols=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?store.columnsLandscape():store.columnsPortrait();
        grid.setColumnCount(cols);
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        UsageTools.Snapshot usage=UsageTools.snapshot(this);
        List<AppCatalog.Entry> apps=AppCatalog.listLaunchable(this);
        ArrayList<AppCatalog.Entry> shown=new ArrayList<>();
        for(AppCatalog.Entry e:apps){
            if(AppCatalog.matchesSmart(e,kind,usage)) shown.add(e);
            else if(!usage.available && "RECENT".equals(kind) && store.ownLastLaunch(e.pkg)>0L) shown.add(e);
            else if(!usage.available && "FREQUENT".equals(kind) && store.ownLaunchCount(e.pkg)>0) shown.add(e);
        }

        if("RECENT".equals(kind)) {
            if(usage.available) shown.sort((a,c)->Long.compare(usage.lastUsed.getOrDefault(c.pkg,0L),usage.lastUsed.getOrDefault(a.pkg,0L)));
            else shown.sort((a,c)->Long.compare(store.ownLastLaunch(c.pkg),store.ownLastLaunch(a.pkg)));
            // Keep the smart folder quick to render on small phones and focused on genuinely recent entries.
            if(shown.size()>30)shown.subList(30,shown.size()).clear();
        }
        if("FREQUENT".equals(kind)) {
            if(usage.available) shown.sort((a,c)->Integer.compare(usage.launches30d.getOrDefault(c.pkg,0),usage.launches30d.getOrDefault(a.pkg,0)));
            else shown.sort((a,c)->Integer.compare(store.ownLaunchCount(c.pkg),store.ownLaunchCount(a.pkg)));
            // "Frequently used" should remain a useful shortlist instead of becoming every app used once this month.
            if(shown.size()>30)shown.subList(30,shown.size()).clear();
        }

        for(AppCatalog.Entry e:shown) addCell(grid,e);
        if(shown.isEmpty()){
            String message="該当するアプリがありません";
            if("UNUSED30".equals(kind)&&!usage.available) message="30日未使用の判定には\n使用状況へのアクセスが必要です";
            else if(("RECENT".equals(kind)||"FREQUENT".equals(kind))&&!usage.available) message="端末全体の使用状況は未許可です\n巨大フォルダから起動した履歴だけを表示します";
            TextView empty=text(message,15,0xFFC8C8D0);
            empty.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(0,1f),GridLayout.spec(0,cols,1f));
            lp.width=0;
            lp.height=Ui.dp(this,150);
            grid.addView(empty,lp);
        }
        Ui.applySafeInsets(root);
        setContentView(root);
        Ui.applyFont(root,tf);
    }

    private TextView text(String s,float sp,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);t.setTypeface(tf);return t;}

    private void addCell(GridLayout grid,AppCatalog.Entry e){
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(Ui.dp(this,3),Ui.dp(this,5),Ui.dp(this,3),Ui.dp(this,4));
        cell.setBackground(Ui.rounded(0x22222222,18,this));
        boolean compact=getResources().getConfiguration().screenWidthDp<=360||getResources().getConfiguration().screenHeightDp<=700;
        ImageView iv=new ImageView(this);
        iv.setImageDrawable(AppCatalog.icon(this,e));
        TextView tv=text(e.label,compact?11:12,Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        cell.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,compact?44:48),Ui.dp(this,compact?44:48)));
        cell.addView(tv,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?30:34)));
        cell.setOnClickListener(v->{
            boolean ok=AppLaunchManager.launch(this,store,e.pkg,e.activity,e.label,Settings.canDrawOverlays(this));
            if(ok)refreshOnNextResume=true;
            else Toast.makeText(this,"起動できませんでした",Toast.LENGTH_SHORT).show();
        });
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
        lp.width=0;
        lp.height=Ui.dp(this,compact?90:102);
        lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        int margin=Ui.dp(this,compact?2:3);
        lp.setMargins(margin,margin,margin,margin);
        grid.addView(cell,lp);
    }
}
