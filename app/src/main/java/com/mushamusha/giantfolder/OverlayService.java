package com.mushamusha.giantfolder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.res.Configuration;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class OverlayService extends Service {
    public static final String ACTION_SHOW="com.mushamusha.giantfolder.SHOW";
    private static final String CHANNEL="return_tab";
    private WindowManager wm;
    private LinearLayout root,quick;
    private TextView tab;
    private WindowManager.LayoutParams lp;
    private DataStore store;
    private boolean expanded=false;

    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        store=new DataStore(this,false);
        createChannel();
    }

    @Override public IBinder onBind(Intent i){return null;}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(101,notification());
        if(!show()){
            ReturnReminderManager.showCurrent(this);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void createChannel(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"戻るベロ",NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("巨大フォルダへ戻るベロを表示します");
        nm.createNotificationChannel(ch);
    }

    private Notification notification(){
        Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("巨大フォルダ")
                .setContentText("タップすると巨大フォルダへ戻れます")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private boolean show(){
        if(root!=null) return true;
        if(!Settings.canDrawOverlays(this)) return false;
        boolean left="left".equals(store.overlaySide());
        root=new LinearLayout(this);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setOrientation(LinearLayout.HORIZONTAL);
        int quickWidth=getResources().getConfiguration().screenWidthDp<=360?154:176;
        quick=new LinearLayout(this);
        quick.setOrientation(LinearLayout.VERTICAL);
        quick.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));
        quick.setBackground(Ui.rounded(Ui.withAlpha(0xFF202024,94),18,this));
        quick.setVisibility(View.GONE);
        buildQuick();

        tab=new TextView(this);
        tab.setText(left?"›":"‹");
        tab.setTextSize(28);
        tab.setTextColor(Color.WHITE);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(Ui.appTypeface(this));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Ui.withAlpha(store.keyColor(),store.overlayAlpha()));
        float r=Ui.dp(this,22);
        if(left) bg.setCornerRadii(new float[]{0,0,r,r,r,r,0,0});
        else bg.setCornerRadii(new float[]{r,r,0,0,0,0,r,r});
        tab.setBackground(bg);

        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(Ui.dp(this,store.overlayWidth()),Ui.dp(this,store.overlayHeight()));
        if(left){
            root.addView(tab,tp);
            root.addView(quick,new LinearLayout.LayoutParams(Ui.dp(this,quickWidth),-2));
        }else{
            root.addView(quick,new LinearLayout.LayoutParams(Ui.dp(this,quickWidth),-2));
            root.addView(tab,tp);
        }

        lp=new WindowManager.LayoutParams(
                Ui.dp(this,store.overlayWidth()),
                Ui.dp(this,store.overlayHeight()),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity=(left?Gravity.START:Gravity.END)|Gravity.TOP;
        lp.x=0;
        lp.y=clampY(store.overlayY());

        final float[] down={0,0};
        final int[] startY={0};
        final boolean[] moved={false};
        tab.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                down[0]=e.getRawX();down[1]=e.getRawY();startY[0]=lp.y;moved[0]=false;return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dx=e.getRawX()-down[0],dy=e.getRawY()-down[1];
                if(Math.abs(dy)>Ui.dp(this,5)&&Math.abs(dy)>Math.abs(dx)){
                    moved[0]=true;
                    lp.y=clampY(startY[0]+(int)dy);
                    try{wm.updateViewLayout(root,lp);}catch(Exception ignored){}
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                float dx=e.getRawX()-down[0];
                boolean inward=left?dx>Ui.dp(this,40):dx<-Ui.dp(this,40);
                if(inward){openMain();return true;}
                if(!moved[0])toggleQuick();
                store.setOverlayY(lp.y);
                return true;
            }
            return true;
        });

        try{
            wm.addView(root,lp);
            return true;
        }catch(Exception e){
            root=null;quick=null;tab=null;lp=null;
            return false;
        }
    }

    private int clampY(int y){
        int actualHeight=root!=null&&root.getHeight()>0?root.getHeight():Ui.dp(this,store.overlayHeight());
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        int safeTop=0,safeBottom=0;
        if(Build.VERSION.SDK_INT>=30){
            try{
                WindowMetrics metrics=wm.getCurrentWindowMetrics();
                screenHeight=metrics.getBounds().height();
                Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                safeTop=insets.top;safeBottom=insets.bottom;
            }catch(Exception ignored){}
        }
        int max=Math.max(safeTop,screenHeight-safeBottom-actualHeight);
        return Math.max(safeTop,Math.min(max,y));
    }

    private void buildQuick(){
        if(quick==null)return;
        quick.removeAllViews();
        TextView h=txt("最近使ったアプリ",12,0xFFDADAE0);
        h.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,5));
        quick.addView(h);
        JSONArray a=store.recentLaunches();
        int added=0;
        for(int i=0;i<a.length()&&added<5;i++){
            try{
                JSONObject o=a.getJSONObject(i);
                String name=o.optString("name","アプリ"),pkg=o.optString("package"),act=o.optString("activity");
                if(pkg.isEmpty()||getPackageManager().getLaunchIntentForPackage(pkg)==null) continue;
                TextView row=txt(name,13,Color.WHITE);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(this,10),0,Ui.dp(this,8),0);
                row.setBackground(Ui.rounded(0x182F2F2F,12,this));
                quick.addView(row,new LinearLayout.LayoutParams(-1,Ui.dp(this,38)));
                row.setOnClickListener(v->{
                    if(launchFromQuick(pkg,act,name)) toggleQuick();
                    else Toast.makeText(this,"起動できませんでした",Toast.LENGTH_SHORT).show();
                });
                added++;
            }catch(Exception ignored){}
        }
        if(added==0){
            TextView none=txt("まだ履歴がありません",12,0xFFBBBBBF);
            quick.addView(none,new LinearLayout.LayoutParams(-1,Ui.dp(this,38)));
        }
    }

    private boolean launchFromQuick(String pkg,String activity,String name){
        boolean launched=AppLaunchManager.launchFromActiveOverlay(this,store,pkg,activity,name);
        if(launched)buildQuick();
        return launched;
    }

    private TextView txt(String s,float sp,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(Ui.appTypeface(this));return t;
    }

    private void toggleQuick(){
        if(root==null||lp==null)return;
        expanded=!expanded;
        quick.setVisibility(expanded?View.VISIBLE:View.GONE);
        int width=Ui.dp(this,store.overlayWidth()+(expanded?(getResources().getConfiguration().screenWidthDp<=360?154:176):0));
        lp.width=width;
        lp.height=expanded?WindowManager.LayoutParams.WRAP_CONTENT:Ui.dp(this,store.overlayHeight());
        try{wm.updateViewLayout(root,lp);}catch(Exception ignored){}
        // WRAP_CONTENT is known only after layout; clamp again so the expanded quick panel cannot fall below the screen.
        root.post(()->{
            if(root==null||lp==null)return;
            int clamped=clampY(lp.y);
            if(clamped!=lp.y){lp.y=clamped;try{wm.updateViewLayout(root,lp);}catch(Exception ignored){}}
        });
    }

    private void openMain(){
        try{
            ReturnReminderManager.endSession(this);
            Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
            hide();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }catch(Exception e){
            ReturnReminderManager.showCurrent(this);
        }
    }

    private void hide(){
        if(root!=null){try{wm.removeView(root);}catch(Exception ignored){}}
        root=null;quick=null;tab=null;lp=null;expanded=false;
    }


    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        // Rebuild so compact width, height and screen bounds are recalculated after rotation/display changes.
        boolean wasShowing=root!=null;
        if(wasShowing){
            hide();
            if(!show()){
                ReturnReminderManager.showCurrent(this);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }

    @Override public void onDestroy(){
        boolean hadOverlay=root!=null;
        hide();
        // If a previously visible overlay disappears unexpectedly, keep the notification fallback.
        // When show() already failed, onStartCommand has shown the fallback itself, so do not notify twice.
        if(hadOverlay)ReturnReminderManager.showCurrent(this);
        super.onDestroy();
    }
}
