package com.mushamusha.giantfolder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

public final class ReturnReminderManager {
    public static final String ACTION_SNOOZE="com.mushamusha.giantfolder.REMINDER_SNOOZE";
    public static final String ACTION_SUPPRESS="com.mushamusha.giantfolder.REMINDER_SUPPRESS";
    public static final String ACTION_SHOW="com.mushamusha.giantfolder.REMINDER_SHOW";
    public static final String EXTRA_SESSION="session";

    private static final String CHANNEL="return_reminder";
    private static final int NOTIFICATION_ID=202;
    private static final int ALARM_REQUEST=4404;
    private static final String PREF="return_reminder_state";
    private static final String KEY_SESSION="session";
    private static final String KEY_SUPPRESSED="suppressed";
    private static final String KEY_APP_NAME="app_name";

    private ReturnReminderManager(){}

    private static SharedPreferences state(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    public static long beginSession(Context c,String appName){
        cancelAlarm(c);
        cancelNotification(c);
        long session=Math.max(System.currentTimeMillis(),state(c).getLong(KEY_SESSION,0L)+1L);
        state(c).edit().putLong(KEY_SESSION,session).putBoolean(KEY_SUPPRESSED,false).putString(KEY_APP_NAME,appName==null?"":appName).apply();
        return session;
    }

    public static void endSession(Context c){
        cancelAlarm(c);
        cancelNotification(c);
        state(c).edit().putBoolean(KEY_SUPPRESSED,true).apply();
    }

    public static void showCurrent(Context c){showIfActive(c,state(c).getLong(KEY_SESSION,0L));}

    public static void showIfActive(Context c,long session){
        SharedPreferences s=state(c);
        if(session==0L||s.getLong(KEY_SESSION,0L)!=session||s.getBoolean(KEY_SUPPRESSED,false))return;
        if(!DataStore.returnReminderEnabled(c))return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        createChannel(c);
        int minutes=DataStore.returnReminderSnoozeMinutes(c);
        String app=s.getString(KEY_APP_NAME,"");
        String text=app==null||app.isEmpty()?"巨大フォルダへ戻れます":"「"+app+"」から巨大フォルダへ戻れます";

        PendingIntent open=mainActivityIntent(c,4401);
        PendingIntent snooze=receiverIntent(c,ACTION_SNOOZE,session,4402);
        PendingIntent suppress=receiverIntent(c,ACTION_SUPPRESS,session,4403);
        PendingIntent content=mainActivityIntent(c,4400);

        Notification n=new Notification.Builder(c,CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("巨大フォルダへ戻る")
                .setContentText(text)
                .setContentIntent(content)
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .addAction(0,"開く",open)
                .addAction(0,minutes+"分後",snooze)
                .addAction(0,"今回は通知しない",suppress)
                .build();
        try{((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,n);}catch(Exception ignored){}
    }

    public static void snooze(Context c,long session){
        SharedPreferences s=state(c);
        if(s.getLong(KEY_SESSION,0L)!=session||s.getBoolean(KEY_SUPPRESSED,false))return;
        cancelNotification(c);
        int minutes=DataStore.returnReminderSnoozeMinutes(c);
        Intent i=new Intent(c,ReturnReminderReceiver.class).setAction(ACTION_SHOW).putExtra(EXTRA_SESSION,session);
        PendingIntent pi=PendingIntent.getBroadcast(c,ALARM_REQUEST,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        long trigger=SystemClock.elapsedRealtime()+minutes*60_000L;
        try{am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,pi);}catch(Exception ignored){}
    }

    public static void suppressSession(Context c,long session){
        if(state(c).getLong(KEY_SESSION,0L)!=session)return;
        cancelAlarm(c);
        cancelNotification(c);
        state(c).edit().putBoolean(KEY_SUPPRESSED,true).apply();
    }

    public static void cancelNotification(Context c){
        try{((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);}catch(Exception ignored){}
    }

    private static void cancelAlarm(Context c){
        try{
            Intent i=new Intent(c,ReturnReminderReceiver.class).setAction(ACTION_SHOW);
            PendingIntent pi=PendingIntent.getBroadcast(c,ALARM_REQUEST,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
            if(pi!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();}
        }catch(Exception ignored){}
    }

    private static PendingIntent mainActivityIntent(Context c,int request){
        Intent i=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent receiverIntent(Context c,String action,long session,int request){
        Intent i=new Intent(c,ReturnReminderReceiver.class).setAction(action).putExtra(EXTRA_SESSION,session);
        return PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static void createChannel(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"戻る補助通知",NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("戻るベロを使えないとき、巨大フォルダへ戻るための補助通知です");
        nm.createNotificationChannel(ch);
    }
}
