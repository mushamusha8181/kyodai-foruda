package com.mushamusha.giantfolder;

import android.app.Notification;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

public class BadgeNotificationListener extends NotificationListenerService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshTask=this::refreshNow;

    @Override public void onListenerConnected(){ super.onListenerConnected(); scheduleRefresh(0L); }
    @Override public void onListenerDisconnected(){
        handler.removeCallbacks(refreshTask);
        NotificationBadgeStore.clear();
        broadcastUpdate();
        super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn){ scheduleRefresh(250L); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn){ scheduleRefresh(250L); }
    @Override public void onDestroy(){handler.removeCallbacks(refreshTask);super.onDestroy();}

    private void scheduleRefresh(long delayMs){
        handler.removeCallbacks(refreshTask);
        handler.postDelayed(refreshTask,Math.max(0L,delayMs));
    }

    private void refreshNow(){
        try {
            Map<String,Integer> counts=new HashMap<>();
            StatusBarNotification[] all=getActiveNotifications();
            if(all!=null) for(StatusBarNotification s:all) {
                if(s==null||getPackageName().equals(s.getPackageName())) continue;
                Notification n=s.getNotification();
                if(n==null) continue;
                if((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0) continue;
                // Persistent media/VPN/service notifications are not useful as an "attention" badge.
                if((n.flags & Notification.FLAG_ONGOING_EVENT)!=0) continue;
                String p=s.getPackageName();
                if(p!=null&&!p.isEmpty()) counts.put(p,counts.getOrDefault(p,0)+1);
            }
            NotificationBadgeStore.replaceAll(counts);
            broadcastUpdate();
        } catch(Exception ignored) {}
    }

    private void broadcastUpdate(){
        try{ sendBroadcast(new Intent(NotificationBadgeStore.ACTION_BADGES_UPDATED).setPackage(getPackageName())); }
        catch(Exception ignored){}
    }
}
