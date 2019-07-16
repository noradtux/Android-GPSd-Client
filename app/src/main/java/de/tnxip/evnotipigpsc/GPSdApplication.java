package de.tnxip.evnotipigpsc;

import android.app.Application;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class GPSdApplication extends Application {
    private AsyncTask<String, Void, String> gpsdServiceTask = null;
    private Intent gpsdClientServiceIntent;
    private boolean connected = false;
    protected MainActivity activity = null;
    private boolean NetworkChangeReceiverRegistered = false;
    private static final String NOTIFICATION_CHANNEL = "gpsd_streaming";
    private static final int NOTIFICATION_ID = 1;

    public void setActivity(MainActivity activity) {
        this.activity = activity;
    }

    // NetworkChangeReceiver

    public void registerNetworkChangeReceiver() {
        if (!NetworkChangeReceiverRegistered) {
            this.registerReceiver(new NetworkChangeReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            NetworkChangeReceiverRegistered = true;
            Log.d("NetworkChangeReceiver","registered");

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL) :
                    new Notification.Builder(getApplicationContext());
            builder
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle("Streaming GPS")
                    .setContentText("Waiting for SSID")
                    .build();
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    public void unregisterNetworkChangeReceiver() {
        NetworkChangeReceiverRegistered = false;
    }

    // GpsdService

    public void startGpsdService(String address, String port) {
        Log.d("GPSdApplication","GPSd Starting");
        Toast.makeText(this, "GPSd Starting", Toast.LENGTH_SHORT).show();
        gpsdServiceTask = new StartGpsdServiceTask(this);
        gpsdServiceTask.execute(address, port);
    }

    public void stopGpsdService() {
        if (gpsdServiceTask != null) {
            Log.d("GPSdApplication","GPSd Stopping");
            Toast.makeText(this,"GPSd Stopping", Toast.LENGTH_SHORT).show();
            gpsdServiceTask.cancel(true);
            gpsdServiceTask = null;
        }
        if (gpsdClientServiceIntent != null) {
            unbindService(serviceConnection);
            stopService(gpsdClientServiceIntent);
            gpsdClientServiceIntent = null;
        }
        setServiceConnected(false);
    }

    public void setServiceConnected(boolean connected) {
        this.connected = connected;
        if (activity != null) {
            activity.setServiceConnectedUI(connected);
        }
    }

    public boolean getServiceConnected() {
        return this.connected;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        private LoggingCallback logger = new LoggingCallback() {
            @Override
            public void log(final String message) {
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activity.print(message);
                        }
                    });
                }
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
        }
    };

    private static class StartGpsdServiceTask extends AsyncTask<String, Void, String> {
        private WeakReference<GPSdApplication> appRef;
        private int port;

        StartGpsdServiceTask(GPSdApplication app) {
            appRef = new WeakReference<>(app);
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
            GPSdApplication app = appRef.get();
            if (app == null)
                return;
            if (app.activity != null)
                app.activity.print(result != null ? result : "StartGpsdServiceTask was cancelled");
            app.setServiceConnected(false);
        }

        @Override
        protected void onPostExecute(String address) {
            GPSdApplication app = appRef.get();
            if (app == null)
                return;
            Intent intent = new Intent(app, GpsdClientService.class);
            intent.putExtra(GpsdClientService.GPSD_SERVER_ADDRESS, address)
                    .putExtra(GpsdClientService.GPSD_SERVER_PORT, port);
            if (app.activity != null)
                app.activity.print("Streaming to " + address + ":" + port);
            try {
                if (!app.bindService(intent, app.serviceConnection, BIND_AUTO_CREATE)) {
                    throw new RuntimeException("Failed to bind to service");
                }
                if (app.startService(intent) == null) {
                    app.unbindService(app.serviceConnection);
                    throw new RuntimeException("Failed to start service");
                }
                app.gpsdClientServiceIntent = intent;
            } catch (RuntimeException e) {
                app.setServiceConnected(false);
                if (app.activity != null)
                    app.activity.print(e.getMessage());
            }
        }

        // Helper

        private static int validatePort(String value) {
            int port = Integer.parseInt(value);
            if (port <= 0 || port > 65535)
                throw new NumberFormatException("Invalid port");
            return port;
        }
    }
}
