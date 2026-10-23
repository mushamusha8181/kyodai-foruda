package com.mushamusha.giantfolder;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UsageTools {
    private static final long CACHE_MS=30_000L;
    private static Snapshot cached;
    private static long cachedAt=0L;
    private UsageTools() {}

    public static class Snapshot {
        public boolean available=false;
        public final Map<String,Long> lastUsed=new HashMap<>();
        public final Map<String,Integer> launches30d=new HashMap<>();
    }

    public static boolean hasAccess(Context c) {
        try {
            AppOpsManager a=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
            if(a==null) return false;
            int mode=a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode==AppOpsManager.MODE_ALLOWED;
        } catch(Exception e) { return false; }
    }

    /** Invalidates the short cache after GiantFolder itself launches an app. */
    public static synchronized void invalidate(){ cached=null;cachedAt=0L; }

    public static Snapshot snapshot(Context c) {
        if(!hasAccess(c)) {
            invalidate();
            return new Snapshot();
        }
        long now=System.currentTimeMillis();
        synchronized(UsageTools.class){
            if(cached!=null&&now-cachedAt<CACHE_MS) return copyOf(cached);
        }

        Snapshot s=new Snapshot();
        long since=now-90L*24L*60L*60L*1000L;
        try {
            UsageStatsManager um=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
            if(um==null) return s;
            List<UsageStats> stats=um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,since,now);
            if(stats==null) return s;
            for(UsageStats u:stats) {
                if(u==null||u.getPackageName()==null)continue;
                long old=s.lastUsed.getOrDefault(u.getPackageName(),0L);
                if(u.getLastTimeUsed()>old) s.lastUsed.put(u.getPackageName(),u.getLastTimeUsed());
            }
            long month=now-30L*24L*60L*60L*1000L;
            UsageEvents events=um.queryEvents(month,now);
            if(events==null) return s;
            UsageEvents.Event ev=new UsageEvents.Event();
            while(events.hasNextEvent()) {
                events.getNextEvent(ev);
                // MOVE_TO_FOREGROUND is available on every supported Android version and maps to the resumed event.
                if(ev.getEventType()==UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    String p=ev.getPackageName();
                    if(p!=null&&!p.isEmpty()) s.launches30d.put(p,s.launches30d.getOrDefault(p,0)+1);
                }
            }
            // Permission alone is not enough: expose usage data only after both queries completed successfully.
            s.available=true;
        } catch(Exception ignored) {
            s.available=false;
            s.lastUsed.clear();
            s.launches30d.clear();
        }
        synchronized(UsageTools.class){cached=copyOf(s);cachedAt=now;}
        return s;
    }

    private static Snapshot copyOf(Snapshot src){
        Snapshot out=new Snapshot();
        out.available=src!=null&&src.available;
        if(src!=null){out.lastUsed.putAll(src.lastUsed);out.launches30d.putAll(src.launches30d);}
        return out;
    }
}
