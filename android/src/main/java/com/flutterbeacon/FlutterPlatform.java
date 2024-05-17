package com.flutterbeacon;
import android.bluetooth.BluetoothAdapter;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.logging.LogManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FlutterPlatform {
  private static final String TAG = "FlutterPlatform";
  private final WeakReference<Activity> activityWeakReference;
  
  FlutterPlatform(Activity activity) {
    activityWeakReference = new WeakReference<>(activity);
  }
  
  private Activity getActivity() {
    return activityWeakReference.get();
  }
  
  void openLocationSettings() {
    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getActivity().startActivity(intent);
  }

  void openBluetoothSettings() {
    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    getActivity().startActivityForResult(intent, FlutterBeaconPlugin.REQUEST_CODE_BLUETOOTH);
  }

  void requestAuthorization() {
    ActivityCompat.requestPermissions(getActivity(), new String[]{
        Manifest.permission.ACCESS_COARSE_LOCATION,
    }, FlutterBeaconPlugin.REQUEST_CODE_LOCATION);
  }

  boolean checkLocationServicesPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return ContextCompat.checkSelfPermission(getActivity(),
          Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    return true;
  }

  boolean checkLocationServicesIfEnabled() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
      return locationManager != null && locationManager.isLocationEnabled();
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      int mode = Settings.Secure.getInt(getActivity().getContentResolver(), Settings.Secure.LOCATION_MODE,
          Settings.Secure.LOCATION_MODE_OFF);
      return (mode != Settings.Secure.LOCATION_MODE_OFF);
    }

    return true;
  }

  @SuppressLint("MissingPermission")
  boolean checkBluetoothIfEnabled() {
    BluetoothManager bluetoothManager = (BluetoothManager)
        getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
    if (bluetoothManager == null) {
      throw new RuntimeException("No bluetooth service");
    }

    BluetoothAdapter adapter = bluetoothManager.getAdapter();

    return (adapter != null) && (adapter.isEnabled());
  }

  boolean isBroadcastSupported() {
    return BeaconTransmitter.checkTransmissionSupported(getActivity()) == 0;
  }
  
  boolean shouldShowRequestPermissionRationale(String permission) {
    return ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), permission);
  }

//  @SuppressLint("InlinedApi")
  void requestPermissions() {
    List<String> permissions = new ArrayList<>();
    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
    permissions.add(Manifest.permission.BLUETOOTH_SCAN);
    permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
    permissions.add(Manifest.permission.FOREGROUND_SERVICE);
    permissions.add(Manifest.permission.POST_NOTIFICATIONS);
    permissions.add(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
    permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    List<String> permissionsToRequest = new ArrayList<>();
    for (String permission : permissions) {
      if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(permission);
      }
    }

    if (!permissionsToRequest.isEmpty()) {
      ActivityCompat.requestPermissions(getActivity(), permissionsToRequest.toArray(new String[0]), FlutterBeaconPlugin.REQUEST_PERMISSIONS);
    } else {
      LogManager.i(TAG, "All permissions are already granted");
//      Toast.makeText(this, "All permissions are already granted", Toast.LENGTH_SHORT).show();
    }
  }

//  Map<String, Boolean> checkPermissions() {
  boolean checkPermissions() {
    List<String> permissions = new ArrayList<>();
    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
    permissions.add(Manifest.permission.BLUETOOTH_SCAN);
    permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
    permissions.add(Manifest.permission.FOREGROUND_SERVICE);
    permissions.add(Manifest.permission.POST_NOTIFICATIONS);
    permissions.add(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
    permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

//    Map<String, Boolean> permissionStatus = new HashMap<>();
//    for (String permission : permissions) {
//      boolean granted = ContextCompat.checkSelfPermission(getActivity(), permission) == PackageManager.PERMISSION_GRANTED;
//      permissionStatus.put(permission, granted);
//    }
//    return permissionStatus;

    boolean isAllGranted = true;
    for (String permission : permissions) {
      if (!(ContextCompat.checkSelfPermission(getActivity(), permission) == PackageManager.PERMISSION_GRANTED)) {
        isAllGranted = false;
      }
    }

    return isAllGranted;
  }
}
