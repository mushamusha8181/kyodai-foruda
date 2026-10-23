package com.mushamusha.giantfolder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Centralizes external app launching so usage history, return UI and cleanup stay consistent. */
public final class AppLaunchManager {
    private AppLaunchManager() {}

    public static boolean launch(Context c, DataStore store, ItemModel m, boolean tryOverlay) {
        if (m == null) return false;
        return launch(c, store, m.packageName, m.activityName, m.name, tryOverlay);
    }

    public static boolean launch(Context c, DataStore store, String pkg, String activity, String name, boolean tryOverlay) {
        if (pkg == null || pkg.isEmpty()) return false;

        ReturnReminderManager.beginSession(c, name);
        boolean overlayStarted = false;
        if (tryOverlay && Settings.canDrawOverlays(c)) {
            try {
                c.startForegroundService(new Intent(c, OverlayService.class).setAction(OverlayService.ACTION_SHOW));
                overlayStarted = true;
            } catch (Exception ignored) {
                overlayStarted = false;
            }
        }
        if (!overlayStarted) ReturnReminderManager.showCurrent(c);

        boolean launched = startExternal(c,pkg,activity);

        if (launched) {
            recordLaunch(store,pkg,activity,name);
            return true;
        }

        ReturnReminderManager.endSession(c);
        if (overlayStarted) {
            try { c.stopService(new Intent(c, OverlayService.class)); } catch (Exception ignored) {}
        }
        return false;
    }
    /** Launches from the already-running overlay without trying to start a second overlay/helper notification. */
    public static boolean launchFromActiveOverlay(Context c, DataStore store, String pkg, String activity, String name) {
        if(pkg==null||pkg.isEmpty())return false;
        ReturnReminderManager.beginSession(c,name);
        boolean launched=startExternal(c,pkg,activity);
        if(launched){
            recordLaunch(store,pkg,activity,name);
            return true;
        }
        ReturnReminderManager.endSession(c);
        return false;
    }

    static boolean startExternal(Context c,String pkg,String activity){
        if(pkg==null||pkg.isEmpty())return false;
        if(activity!=null&&!activity.isEmpty()){
            try{
                Intent explicit=new Intent().setComponent(new ComponentName(pkg,activity)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(explicit);
                return true;
            }catch(Exception ignored){}
        }
        try{
            Intent fallback=c.getPackageManager().getLaunchIntentForPackage(pkg);
            if(fallback!=null){fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(fallback);return true;}
        }catch(Exception ignored){}
        return false;
    }

    static void recordLaunch(DataStore store,String pkg,String activity,String name){
        if(store==null||pkg==null||pkg.isEmpty())return;
        ItemModel temp=new ItemModel();
        temp.type=ItemModel.TYPE_APP;
        temp.packageName=pkg;
        temp.activityName=activity==null?"":activity;
        temp.name=name==null?"":name;
        store.recordLaunch(temp);
        UsageTools.invalidate();
    }

}
