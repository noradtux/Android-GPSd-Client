package de.tnxip.evnotipigpsc;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;


public class MainActivity extends Activity {
    private static final int REQUEST_CODE_FINE_LOCATION = 0;
    private static final String SERVER_ADDRESS = "SERVER_ADDRESS";
    private static final String SERVER_PORT = "SERVER_PORT";
    private static final String NETWORK_SSID = "NETWORK_SSID";
    private static final String MAIN_PREFS = "MAIN_PREFS";
    private Intent gpsdClientServiceIntent;
    private SharedPreferences preferences;
    private TextView textView;
    private TextView networkSsidTextView;
    private TextView serverAddressTextView;
    private TextView serverPortTextView;
    private Button startStopButton;
    private boolean connected;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        private LoggingCallback logger = new LoggingCallback() {
            @Override
            public void log(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        print(message);
                    }
                });
            }
        };

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GpsdClientService.Binder binder = (GpsdClientService.Binder)service;
            binder.setLoggingCallback(logger);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logger.log("GpsdClientService died");
            setServiceConnected(false);
            startStopButton.setEnabled(true);
        }
    };
    private AsyncTask<String, Void, String> gpsdServiceTask = new StartGpsdServiceTask(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        textView.setMovementMethod(new ScrollingMovementMethod());
        networkSsidTextView = findViewById(R.id.netSSID);
        serverAddressTextView = findViewById(R.id.serverAddress);
        serverPortTextView = findViewById(R.id.serverPort);
        startStopButton = findViewById(R.id.startStopButton);

        preferences = getSharedPreferences(MAIN_PREFS, MODE_PRIVATE);

        String net_ssid = preferences.getString(NETWORK_SSID, "EVNotiPI-xxxx");
        if (net_ssid != null && !net_ssid.isEmpty()) networkSsidTextView.setText(net_ssid);

        String address = preferences.getString(SERVER_ADDRESS, "192.168.8.205");
        if (address != null && !address.isEmpty()) serverAddressTextView.setText(address);

        int port = preferences.getInt(SERVER_PORT, 5000);
        if (port > 0)
            serverPortTextView.setText(String.valueOf(port));

        LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            print("GPS is not enabled! Go to Settings and enable a location mode with GPS");
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_FINE_LOCATION);
        }

        this.registerReceiver(new NetworkChangeReceiver(this),new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopGpsdService();
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences.Editor editor = preferences.edit();
        try {
            editor.putInt(SERVER_PORT, validatePort(serverPortTextView.getText().toString()));
        } catch (NumberFormatException e) {
            editor.remove(SERVER_PORT);
        }
        editor.putString(SERVER_ADDRESS, serverAddressTextView.getText().toString())
                .putString(NETWORK_SSID, networkSsidTextView.getText().toString())
                .apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CODE_FINE_LOCATION && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            print("GPS access allowed");
        else {
            print("GPS permission denied");
            startStopButton.setEnabled(false);
        }
    }

    public void startStopButtonOnClick(View view) {
        setServiceConnected(!connected);
        if (connected) {
            gpsdServiceTask = new StartGpsdServiceTask(this);
            gpsdServiceTask.execute(serverAddressTextView.getText().toString(), serverPortTextView.getText().toString());
        } else {
            stopGpsdService();
            startStopButton.setEnabled(true);
        }
    }

    private static class StartGpsdServiceTask extends AsyncTask<String, Void, String> {
        private WeakReference<MainActivity> activityRef;
        private int port;

        StartGpsdServiceTask(MainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(String... host) {
            try {
                port = validatePort(host[1]);
            } catch (NumberFormatException e) {
                cancel(false);
                return "Invalid port";
            }
            try {
                return InetAddress.getByName(host[0]).getHostAddress();
            } catch (UnknownHostException e) {
                cancel(false);
                return "Can't resolve " + host[0];
            }
        }

        @Override
        protected void onCancelled(String result) {
            MainActivity activity = activityRef.get();
            if (activity == null)
                return;
            activity.print(result != null ? result : "StartGpsdServiceTask was cancelled");
            activity.setServiceConnected(false);
            activity.startStopButton.setEnabled(true);
        }

        @Override
        protected void onPostExecute(String address) {
            MainActivity activity = activityRef.get();
            if (activity == null)
                return;
            Intent intent = new Intent(activity, GpsdClientService.class);
            intent.putExtra(GpsdClientService.GPSD_SERVER_ADDRESS, address)
                    .putExtra(GpsdClientService.GPSD_SERVER_PORT, port);
            activity.print("Streaming to " + address + ":" + port);
            try {
                if (!activity.bindService(intent, activity.serviceConnection, BIND_AUTO_CREATE)) {
                    throw new RuntimeException("Failed to bind to service");
                }
                if (activity.startService(intent) == null) {
                    activity.unbindService(activity.serviceConnection);
                    throw new RuntimeException("Failed to start service");
                }
                activity.gpsdClientServiceIntent = intent;
            } catch (RuntimeException e) {
                activity.setServiceConnected(false);
                activity.print(e.getMessage());
            }
            activity.startStopButton.setEnabled(true);
        }
    }

    private void stopGpsdService() {
        if (gpsdServiceTask != null) {
            gpsdServiceTask.cancel(true);
            gpsdServiceTask = null;
        }
        if (gpsdClientServiceIntent != null) {
            unbindService(serviceConnection);
            stopService(gpsdClientServiceIntent);
            gpsdClientServiceIntent = null;
        }
    }

    private void setServiceConnected(boolean connected) {
        this.connected = connected;
        startStopButton.setText(connected ? R.string.stop : R.string.start);
        startStopButton.setEnabled(false);
        networkSsidTextView.setEnabled(!connected);
        serverAddressTextView.setEnabled(!connected);
        serverPortTextView.setEnabled(!connected);
    }

    private static int validatePort(String value) {
        int port = Integer.parseInt(value);
        if (port <= 0 || port > 65535)
            throw new NumberFormatException("Invalid port");
        return port;
    }

    private void print(String message) {
        textView.append(message + "\n");
    }

    private static class NetworkChangeReceiver extends BroadcastReceiver {
        private WeakReference<MainActivity> activityRef;

        NetworkChangeReceiver(MainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            MainActivity activity = activityRef.get();
            if (activity == null)
                return;

            String action = intent.getAction();

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
                            String wantSsid = "\"" + activity.networkSsidTextView.getText().toString() + "\"";
                            String ssid = connectionInfo.getSSID();

                            if (!ssid.isEmpty() && ssid.equals(wantSsid)) {
                                Toast.makeText(context, "GPSd Starting", Toast.LENGTH_SHORT).show();
                                activity.gpsdServiceTask = new StartGpsdServiceTask(activity);
                                activity.gpsdServiceTask.execute(activity.serverAddressTextView.getText().toString(), activity.serverPortTextView.getText().toString());
                                activity.setServiceConnected(true);
                            }
                        }
                    } else {
                        Toast.makeText(context, "GPSd Stopping", Toast.LENGTH_SHORT).show();
                        activity.stopGpsdService();
                        activity.setServiceConnected(false);
                        activity.startStopButton.setEnabled(true);
                    }
                    break;
            }
        }
    }
}
