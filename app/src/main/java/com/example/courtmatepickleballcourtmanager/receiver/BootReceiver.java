package com.example.courtmatepickleballcourtmanager.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.courtmatepickleballcourtmanager.util.LightScheduler;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            LightScheduler.rescheduleAll(context);
        }
    }
}
