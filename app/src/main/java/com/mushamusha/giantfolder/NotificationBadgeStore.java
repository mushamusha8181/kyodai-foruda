package com.mushamusha.giantfolder;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/** Process-local snapshot of current notifications. It is intentionally not persisted because badges are ephemeral. */
public final class NotificationBadgeStore {
    public static final String ACTION_BADGES_UPDATED="com.mushamusha.giantfolder.BADGES_UPDATED";
    private static final Map<String,Integer> COUNTS=new HashMap<>();
    private NotificationBadgeStore() {}

    public static synchronized int get(String pkg){ return Math.max(0,COUNTS.getOrDefault(pkg,0)); }

    public static synchronized void replaceAll(Map<String,Integer> counts){
        COUNTS.clear();
        if(counts==null)return;
        for(Map.Entry<String,Integer> e:counts.entrySet()){
            if(e.getKey()!=null&&!e.getKey().isEmpty()&&e.getValue()!=null&&e.getValue()>0) COUNTS.put(e.getKey(),e.getValue());
        }
    }

    public static synchronized void clear(){ COUNTS.clear(); }

    public static boolean hasAccess(Context c){
        ComponentName ours=new ComponentName(c,BadgeNotificationListener.class);
        if(Build.VERSION.SDK_INT>=27){
            try{
                NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
                return nm!=null&&nm.isNotificationListenerAccessGranted(ours);
            }catch(Exception ignored){}
        }
        // Android 8.0 fallback.
        String enabled=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        if(TextUtils.isEmpty(enabled)) return false;
        for(String flat:enabled.split(":")){
            ComponentName cn=ComponentName.unflattenFromString(flat);
            if(ours.equals(cn)) return true;
        }
        return false;
    }
}
