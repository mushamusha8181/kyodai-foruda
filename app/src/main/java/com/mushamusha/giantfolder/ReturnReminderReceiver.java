package com.mushamusha.giantfolder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReturnReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        long session=intent.getLongExtra(ReturnReminderManager.EXTRA_SESSION,0L);
        if(ReturnReminderManager.ACTION_SNOOZE.equals(action)){
            ReturnReminderManager.snooze(context,session);
        }else if(ReturnReminderManager.ACTION_SUPPRESS.equals(action)){
            ReturnReminderManager.suppressSession(context,session);
        }else if(ReturnReminderManager.ACTION_SHOW.equals(action)){
            ReturnReminderManager.showIfActive(context,session);
        }
    }
}
