package de.tnxip.evnotipigpsc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GPSdApplication app = (GPSdApplication) context.getApplicationContext();

        Log.d("BootCompleteReceiver","Boot Completed");
        app.registerNetworkChangeReceiver();
    }
}
