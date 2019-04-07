package de.tnxip.evnotipigpsc;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends Activity {
    private static final int REQUEST_CODE_FINE_LOCATION = 0;
    protected static final String SERVER_ADDRESS = "SERVER_ADDRESS";
    protected static final String SERVER_PORT = "SERVER_PORT";
    protected static final String NETWORK_SSID = "NETWORK_SSID";
    protected static final String MAIN_PREFS = "MAIN_PREFS";
    private SharedPreferences preferences;
    private TextView textView;
    private TextView networkSsidTextView;
    private TextView serverAddressTextView;
    private TextView serverPortTextView;
    private Button startStopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GPSdApplication app = (GPSdApplication) getApplication();

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

        setServiceConnectedUI(app.getServiceConnected());
        app.setActivity(this);
        //this.registerReceiver(new NetworkChangeReceiver(),new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        networkSsidTextView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) return;
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(NETWORK_SSID, networkSsidTextView.getText().toString()).apply();
            }
        });
        serverAddressTextView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) return;
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(SERVER_ADDRESS, serverAddressTextView.getText().toString()).apply();
            }
        });
        serverPortTextView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) return;
                SharedPreferences.Editor editor = preferences.edit();
                try {
                    editor.putInt(SERVER_PORT, validatePort(serverPortTextView.getText().toString())).apply();
                } catch (NumberFormatException e) {
                   editor.remove(SERVER_PORT);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GPSdApplication app = (GPSdApplication) getApplication();
        app.stopGpsdService();
        app.setActivity(null);
    }

    /*@Override
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
    }*/

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CODE_FINE_LOCATION && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            print("GPS access allowed");
        else {
            print("GPS permission denied");
            startStopButton.setEnabled(false);
        }
    }

    public void setServiceConnectedUI(boolean connected) {
        startStopButton.setText(connected ? R.string.stop : R.string.start);
        //startStopButton.setEnabled(false);
        networkSsidTextView.setEnabled(!connected);
        serverAddressTextView.setEnabled(!connected);
        serverPortTextView.setEnabled(!connected);
    }

    public void startStopButtonOnClick(View view) {
        GPSdApplication app = (GPSdApplication) getApplication();

        app.setServiceConnected(!app.getServiceConnected());
        if (app.getServiceConnected()) {
            app.startGpsdService(serverAddressTextView.getText().toString(), serverPortTextView.getText().toString());
        } else {
            app.stopGpsdService();
            startStopButton.setEnabled(true);
        }
    }

    private static int validatePort(String value) {
        int port = Integer.parseInt(value);
        if (port <= 0 || port > 65535)
            throw new NumberFormatException("Invalid port");
        return port;
    }

    public void print(String message) {
        textView.append(message + "\n");
    }

}
