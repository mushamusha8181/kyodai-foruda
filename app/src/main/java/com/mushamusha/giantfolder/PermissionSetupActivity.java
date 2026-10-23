package com.mushamusha.giantfolder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PermissionSetupActivity extends Activity {
    private static final int REQ_NOTIFY=4101, REQ_OVERLAY=4102, REQ_USAGE=4103, REQ_BADGES=4104;
    private int stage=0;
    private TextView status;
    private DataStore store;
    private boolean busy=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); store=new DataStore(this,false);
        if(b!=null) stage=b.getInt("stage",0);
        buildUi();
        // 権限画面はボタン操作から順番に開く。自動実行との二重起動を避ける。
    }

    @Override protected void onSaveInstanceState(Bundle out){ super.onSaveInstanceState(out); out.putInt("stage",stage); }

    private void buildUi(){
        boolean compact=getResources().getConfiguration().screenWidthDp<=360||getResources().getConfiguration().screenHeightDp<=700;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(Ui.dp(this,compact?12:16),Ui.dp(this,compact?14:20),Ui.dp(this,compact?12:16),Ui.dp(this,compact?12:16)); root.setBackgroundColor(0xFF18181B);
        TextView title=t("初回の権限設定",compact?22:24,Color.WHITE); root.addView(title);
        TextView note=t("巨大フォルダの全機能を使うため、必要な権限を最初に順番に確認します。許可できない項目があってもアプリ自体は使えます。",compact?13:14,0xFFD7D7DE); note.setPadding(0,Ui.dp(this,compact?6:8),0,Ui.dp(this,compact?8:12)); root.addView(note);
        status=t(statusText(),compact?13:14,0xFFF0F0F5); status.setLineSpacing(0,1.2f); root.addView(status,new LinearLayout.LayoutParams(-1,0,1));
        Button next=new Button(this); next.setText("権限設定を続ける"); next.setAllCaps(false); next.setOnClickListener(v->runNext()); root.addView(next,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?44:48)));
        Button later=new Button(this); later.setText("いったん閉じる（次回また表示）"); later.setAllCaps(false); later.setOnClickListener(v->finish()); root.addView(later,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?42:44)));
        Ui.applySafeInsets(root); setContentView(root); Ui.applyFont(root,Ui.appTypeface(this));
    }

    private TextView t(String s,float sp,int color){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v; }

    private String statusText(){
        return "必要な項目\n\n"+
                "・通知："+(hasNotificationPermission()?"許可済み":"未許可")+"\n"+
                "・他のアプリの上に表示："+(Settings.canDrawOverlays(this)?"許可済み":"未許可")+"\n"+
                "・使用状況へのアクセス："+(UsageTools.hasAccess(this)?"許可済み":"未許可")+"\n"+
                "・通知へのアクセス："+(NotificationBadgeStore.hasAccess(this)?"許可済み":"未許可")+"\n\n"+
                "※ 生体認証は利用時に端末の生体認証機能を使います。\n"+
                "※ 背景画像・アイコン画像はAndroidのファイル選択画面を使うため、写真やストレージ全体への権限は取りません。";
    }

    private void refresh(){ if(status!=null) status.setText(statusText()); }

    private void runNext(){
        if(busy)return;
        refresh();
        if(stage<=0){
            stage=1;
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){ busy=true;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFY); return; }
        }
        if(stage<=1){
            stage=2;
            if(!Settings.canDrawOverlays(this)){
                busy=true;Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())); startActivityForResult(i,REQ_OVERLAY); return;
            }
        }
        if(stage<=2){
            stage=3;
            if(!UsageTools.hasAccess(this)){ busy=true;startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),REQ_USAGE); return; }
        }
        if(stage<=3){
            stage=4;
            if(!NotificationBadgeStore.hasAccess(this)){ busy=true;startActivityForResult(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),REQ_BADGES); return; }
        }
        store.setPermissionSetupDone(true); refresh();
        new android.app.AlertDialog.Builder(this).setTitle("権限の確認が完了しました").setMessage("必要な設定画面はすべて案内しました。未許可の項目は設定からいつでも再確認できます。")
                .setPositiveButton("巨大フォルダを開く",(d,w)->finish()).show();
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){ super.onRequestPermissionsResult(request,permissions,results); if(request==REQ_NOTIFY){ busy=false;refresh(); runNext(); } }
    @Override protected void onActivityResult(int request,int result,Intent data){ super.onActivityResult(request,result,data); if(request==REQ_OVERLAY||request==REQ_USAGE||request==REQ_BADGES){ busy=false;refresh(); runNext(); } }

    private boolean hasNotificationPermission(){ return Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED; }
}
