package de.tnxip.evnotipigpsc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

public class NetworkChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "NetworkChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        GPSdApplication app = (GPSdApplication) context.getApplicationContext();

        String action = intent.getAction();
        Log.d(TAG,"Got action " + action);
        if (app.activity != null)
            Toast.makeText(app.activity, "Action: "+action, Toast.LENGTH_SHORT).show();
        SharedPreferences preferences = context.getSharedPreferences(MainActivity.MAIN_PREFS, Context.MODE_PRIVATE);

        assert action != null;

        switch(action) {
            case ConnectivityManager.CONNECTIVITY_ACTION:
                ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                boolean isConnected = wifi != null && wifi.isConnected();
                if (isConnected) {
                    final WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    final WifiInfo connectionInfo = wifiManager.getConnectionInfo();

                    if (connectionInfo != null) {
                        String wantSsid = "\"" + preferences.getString(MainActivity.NETWORK_SSID, "") + "\"";
                        String ssid = connectionInfo.getSSID();

                        if (!ssid.isEmpty() && ssid.equals(wantSsid)) {
                            app.startGpsdService(preferences.getString(MainActivity.SERVER_ADDRESS, ""),
                                    Integer.toString(preferences.getInt(MainActivity.SERVER_PORT, -1)));
                        }
                    }
                } else {
                    app.stopGpsdService();
                }
                break;
        }
    }
}
