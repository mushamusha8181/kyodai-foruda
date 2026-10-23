package com.mushamusha.giantfolder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AppCatalog {
    private static final long DAY_MS=24L*60L*60L*1000L;
    private static final int NEW_DAYS=14;
    private AppCatalog() {}

    public static class Entry {
        public String label="", pkg="", activity="";
        public Drawable icon;
        public long installedAt=0L;
        public boolean game=false;
        public String key(){return pkg+"/"+activity;}
    }

    public static List<Entry> listLaunchable(Context c) { return listLaunchable(c,true); }

    /**
     * Lists launcher-visible activities. Smart-folder metadata can be skipped for simple pickers to avoid
     * unnecessary PackageManager work.
     */
    public static List<Entry> listLaunchable(Context c, boolean includeSmartMetadata) {
        ArrayList<Entry> out=new ArrayList<>();
        PackageManager pm=c.getPackageManager();
        Intent q=new Intent(Intent.ACTION_MAIN,null).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris=pm.queryIntentActivities(q,PackageManager.MATCH_ALL);
        Set<String> seen=new HashSet<>();
        Map<String,Long> installCache=new HashMap<>();
        Map<String,Boolean> gameCache=new HashMap<>();
        if(ris==null) return out;
        for(ResolveInfo r:ris) {
            if(r.activityInfo==null) continue;
            if(c.getPackageName().equals(r.activityInfo.packageName)) continue;
            String key=r.activityInfo.packageName+"/"+r.activityInfo.name;
            if(!seen.add(key)) continue;
            Entry e=new Entry();
            e.label=String.valueOf(r.loadLabel(pm));
            e.pkg=r.activityInfo.packageName;
            e.activity=r.activityInfo.name;
            if(includeSmartMetadata){
                if(!installCache.containsKey(e.pkg)||!gameCache.containsKey(e.pkg)){
                    long installed=0L;boolean game=false;
                    try {
                        PackageInfo pi=pm.getPackageInfo(e.pkg,0);
                        installed=pi.firstInstallTime;
                        ApplicationInfo ai=pi.applicationInfo;
                        game=ai!=null&&ai.category==ApplicationInfo.CATEGORY_GAME;
                    } catch(Exception ignored) {}
                    installCache.put(e.pkg,installed);gameCache.put(e.pkg,game);
                }
                e.installedAt=installCache.getOrDefault(e.pkg,0L);
                e.game=gameCache.getOrDefault(e.pkg,false);
            }
            out.add(e);
        }
        Collator coll=Collator.getInstance(Locale.JAPANESE);
        out.sort((a,b)->coll.compare(a.label,b.label));
        return out;
    }

    public static boolean isNew(Context c,String pkg) {
        if(pkg==null||pkg.isEmpty())return false;
        try {
            PackageInfo pi=c.getPackageManager().getPackageInfo(pkg,0);
            return System.currentTimeMillis()-pi.firstInstallTime <= NEW_DAYS*DAY_MS;
        } catch(Exception e) { return false; }
    }

    public static boolean matchesSmart(Entry e, String kind, UsageTools.Snapshot usage) {
        if(e==null)return false;
        if("NEW".equals(kind)) return e.installedAt>0 && System.currentTimeMillis()-e.installedAt <= NEW_DAYS*DAY_MS;
        if("GAMES".equals(kind)) return e.game;
        if("GOOGLE".equals(kind)) return e.pkg.startsWith("com.google.") || e.pkg.equals("com.android.chrome");
        if("SNS".equals(kind)) return isKnownSocial(e);
        if("RECENT".equals(kind)) {
            if(usage==null||!usage.available) return false;
            long last=usage.lastUsed.getOrDefault(e.pkg,0L);
            return last>0L && System.currentTimeMillis()-last<=30L*DAY_MS;
        }
        if("FREQUENT".equals(kind)) return usage!=null && usage.available && usage.launches30d.getOrDefault(e.pkg,0)>0;
        if("UNUSED30".equals(kind)) {
            if(usage==null||!usage.available) return false;
            long now=System.currentTimeMillis();
            long last=usage.lastUsed.getOrDefault(e.pkg,0L);
            if(last>0L)return now-last>30L*DAY_MS;
            // No usage event is not enough by itself: a newly installed app may simply not have been opened yet.
            return e.installedAt>0L && now-e.installedAt>30L*DAY_MS;
        }
        return false;
    }

    private static boolean isKnownSocial(Entry e) {
        String p=e.pkg.toLowerCase(Locale.ROOT);
        if(p.equals("jp.naver.line.android") || p.startsWith("com.instagram.") || p.startsWith("com.facebook.") ||
                p.startsWith("com.whatsapp") || p.startsWith("org.telegram.") || p.startsWith("com.discord") ||
                p.startsWith("com.snapchat.") || p.startsWith("com.twitter.") || p.startsWith("com.zhiliaoapp.musically")) return true;
        String l=e.label==null?"":e.label.trim().toLowerCase(Locale.ROOT);
        return l.equals("line") || l.contains("instagram") || l.contains("facebook") || l.contains("messenger") ||
                l.contains("twitter") || l.equals("x") || l.contains("discord") || l.contains("tiktok") ||
                l.contains("threads") || l.contains("snapchat") || l.contains("whatsapp") || l.contains("telegram");
    }

    public static Drawable icon(Context c, Entry e) {
        if(e==null)return c.getDrawable(android.R.drawable.sym_def_app_icon);
        if(e.icon!=null) return e.icon;
        PackageManager pm=c.getPackageManager();
        try { e.icon=pm.getActivityIcon(new ComponentName(e.pkg,e.activity)); }
        catch(Exception first) {
            try { e.icon=pm.getApplicationIcon(e.pkg); }
            catch(Exception ignored) { e.icon=c.getDrawable(android.R.drawable.sym_def_app_icon); }
        }
        return e.icon;
    }
}
