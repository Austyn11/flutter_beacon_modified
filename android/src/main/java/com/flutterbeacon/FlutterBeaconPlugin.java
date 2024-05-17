package com.flutterbeacon;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.logging.LogManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class FlutterBeaconPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler,
    PluginRegistry.RequestPermissionsResultListener,
    PluginRegistry.ActivityResultListener {
  private static final String TAG = "FlutterBeaconPlugin";
  private static final BeaconParser iBeaconLayout = new BeaconParser()
      .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24");

  static final int REQUEST_CODE_LOCATION = 1234;
  static final int REQUEST_CODE_BLUETOOTH = 5678;

  static final int REQUEST_PERMISSIONS = 4321;
  static final int REQUEST_BACKGROUND_LOCATION = 8765;
  private FlutterPluginBinding flutterPluginBinding;
  private ActivityPluginBinding activityPluginBinding;

  private FlutterBeaconScanner beaconScanner;
  private FlutterBeaconBroadcast beaconBroadcast;
  private FlutterPlatform platform;
  
  private BeaconManager beaconManager;
  Result flutterResult;
  private Result flutterResultBluetooth;
  private EventChannel.EventSink eventSinkLocationAuthorizationStatus;

  private MethodChannel channel;
  private EventChannel eventChannel;
  private EventChannel eventChannelMonitoring;
  private EventChannel eventChannelBluetoothState;
  private EventChannel eventChannelAuthorizationStatus;
//  private EventChannel eventChannelPermissions;
  public FlutterBeaconPlugin() {

  }

  public static void registerWith(Registrar registrar) {
    final FlutterBeaconPlugin instance = new FlutterBeaconPlugin();
    instance.setupChannels(registrar.messenger(), registrar.activity());
    registrar.addActivityResultListener(instance);
    registrar.addRequestPermissionsResultListener(instance);
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    this.flutterPluginBinding = binding;
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    this.flutterPluginBinding = null;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    this.activityPluginBinding = binding;

    setupChannels(flutterPluginBinding.getBinaryMessenger(), binding.getActivity());
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    teardownChannels();
  }

  BeaconManager getBeaconManager() {
    return beaconManager;
  }

  private void setupChannels(BinaryMessenger messenger, Activity activity) {
    if (activityPluginBinding != null) {
      activityPluginBinding.addActivityResultListener(this);
      activityPluginBinding.addRequestPermissionsResultListener(this);
    }

    beaconManager = BeaconManager.getInstanceForApplication(activity.getApplicationContext());
    if (!beaconManager.getBeaconParsers().contains(iBeaconLayout)) {
      beaconManager.getBeaconParsers().clear();
      beaconManager.getBeaconParsers().add(iBeaconLayout);
    }

    platform = new FlutterPlatform(activity);
    beaconScanner = new FlutterBeaconScanner(this, activity);
    beaconBroadcast = new FlutterBeaconBroadcast(activity, iBeaconLayout);

    channel = new MethodChannel(messenger, "flutter_beacon");
    channel.setMethodCallHandler(this);

    eventChannel = new EventChannel(messenger, "flutter_beacon_event");
    eventChannel.setStreamHandler(beaconScanner.rangingStreamHandler);

    eventChannelMonitoring = new EventChannel(messenger, "flutter_beacon_event_monitoring");
    eventChannelMonitoring.setStreamHandler(beaconScanner.monitoringStreamHandler);

    eventChannelBluetoothState = new EventChannel(messenger, "flutter_bluetooth_state_changed");
    eventChannelBluetoothState.setStreamHandler(new FlutterBluetoothStateReceiver(activity));

    eventChannelAuthorizationStatus = new EventChannel(messenger, "flutter_authorization_status_changed");
    eventChannelAuthorizationStatus.setStreamHandler(locationAuthorizationStatusStreamHandler);
  }

  private void teardownChannels() {
    if (activityPluginBinding != null) {
      activityPluginBinding.removeActivityResultListener(this);
      activityPluginBinding.removeRequestPermissionsResultListener(this);
    }

    platform = null;
    beaconBroadcast = null;

    channel.setMethodCallHandler(null);
    eventChannel.setStreamHandler(null);
    eventChannelMonitoring.setStreamHandler(null);
    eventChannelBluetoothState.setStreamHandler(null);
    eventChannelAuthorizationStatus.setStreamHandler(null);

    channel = null;
    eventChannel = null;
    eventChannelMonitoring = null;
    eventChannelBluetoothState = null;
    eventChannelAuthorizationStatus = null;

    activityPluginBinding = null;
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
    if (call.method.equals("checkPermissions")) {
//      Map<String, Boolean> permissionsGranted = platform.checkPermissions();
      boolean permissionsGranted = checkPermissions();
      result.success(permissionsGranted);
    }

    if (call.method.equals("requestPermissions")) {
      requestPermissions();
//      result.success(true);
    }

    if (call.method.equals("initialize")) {
      if (beaconManager != null && !beaconManager.isBound(beaconScanner.beaconConsumer)) {

        Notification.Builder builder = new Notification.Builder(flutterPluginBinding.getApplicationContext());
        builder.setSmallIcon(R.mipmap.ic_fitforme_launcher);
        builder.setContentTitle("호흡을 측정하고 있습니다.");
        builder.setAutoCancel(false); // true면 Notification 클릭시 삭제
        builder.setOngoing(true);
//        builder.setContentIntent(mainPendingIntent);
//        builder.addAction("종료", connectionClosePendingIntent);
//        builder.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          NotificationChannel channel = new NotificationChannel("My Notification Channel ID",
                  "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT);
          channel.setDescription("My Notification Channel Description");
          NotificationManager notificationManager = (NotificationManager) flutterPluginBinding.getApplicationContext().getSystemService(
                  Context.NOTIFICATION_SERVICE);
          notificationManager.createNotificationChannel(channel);
          builder.setChannelId(channel.getId());
        }

        beaconManager.setAndroidLScanningDisabled(false);
        beaconManager.setForegroundScanPeriod(1000); // 1100??
        beaconManager.setForegroundBetweenScanPeriod(0);

        beaconManager.enableForegroundServiceScanning(builder.build(), 456);
        beaconManager.setEnableScheduledScanJobs(false);

        flutterResult = result;
        beaconManager.bind(beaconScanner.beaconConsumer);

        return;
      }

      result.success(true);
      return;
    }

    if (call.method.equals("isBeaconServiceBound")) {
      if (beaconManager.isAnyConsumerBound()) {
        LogManager.i(TAG, "beaconManager.isAnyConsumerBound() true");
        if (beaconManager.isBound(beaconScanner.beaconConsumer)) {
          LogManager.i(TAG, "beaconManager.isBound(beaconScanner.beaconConsumer) true");
          result.success(0);  // 정상 연결
        } else {
          LogManager.i(TAG, "beaconManager.isBound(beaconScanner.beaconConsumer) false");
          Region region = new Region("FitforMe",null,null,null);  // FitforMe  CustomBeacon
          beaconManager.stopRangingBeacons(region);
//          beaconManager.stopRangingBeacons(region);
//          beaconManager.unbind(beaconScanner.beaconConsumer);
//          beaconManager.disableForegroundServiceScanning(); TODO ??
          result.success(1);  // unbind error
        }
      } else {
        LogManager.i(TAG, "beaconManager.isAnyConsumerBound() false");
        result.success(2);  // not bound
      }
      return;
    }

    if (call.method.equals("initializeAndCheck")) {
      initializeAndCheck(result);
      return;
    }

    if (call.method.equals("setScanPeriod")) {
      int scanPeriod = call.argument("scanPeriod");
      beaconManager.setForegroundScanPeriod(scanPeriod);
//      beaconManager.setBackgroundScanPeriod(scanPeriod);
      try {
        beaconManager.updateScanPeriods();
        result.success(true);
      } catch (RemoteException e) {
        result.success(false);
      }
    }

    if (call.method.equals("setBetweenScanPeriod")) {
      int betweenScanPeriod = call.argument("betweenScanPeriod");
      beaconManager.setForegroundBetweenScanPeriod(betweenScanPeriod);
//      beaconManager.setBackgroundBetweenScanPeriod(betweenScanPeriod);
      try {
        beaconManager.updateScanPeriods();
        result.success(true);
      } catch (RemoteException e) {
        result.success(false);
      }
    }

    if (call.method.equals("setLocationAuthorizationTypeDefault")) {
      // Android does not have the concept of "requestWhenInUse" and "requestAlways" like iOS does,
      // so this method does nothing.
      // (Well, in Android API 29 and higher, there is an "ACCESS_BACKGROUND_LOCATION" option,
      //  which could perhaps be appropriate to add here as an improvement.)
      result.success(true);
      return;
    }

    if (call.method.equals("authorizationStatus")) {
      result.success(platform.checkLocationServicesPermission() ? "ALLOWED" : "NOT_DETERMINED");
      return;
    }

    if (call.method.equals("checkLocationServicesIfEnabled")) {
      result.success(platform.checkLocationServicesIfEnabled());
      return;
    }

    if (call.method.equals("bluetoothState")) {
      try {
        boolean flag = platform.checkBluetoothIfEnabled();
        result.success(flag ? "STATE_ON" : "STATE_OFF");
        return;
      } catch (RuntimeException ignored) {

      }

      result.success("STATE_UNSUPPORTED");
      return;
    }

    if (call.method.equals("setBluetoothState")) {
      BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
      if (bluetoothAdapter == null) {
        LogManager.i(TAG, "Device doesn't support Bluetooth");
        return;
      }

      flutterResult = result;
      try {
        boolean enable = call.argument("enable");
        if (enable) {
          if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activityPluginBinding.getActivity().startActivityForResult(enableBtIntent, REQUEST_CODE_BLUETOOTH);
          } else {
            result.success("STATE_ON");
            flutterResult = null;
          }
        }

        return;
      } catch (RuntimeException ignored) {

      }
      result.success("STATE_UNSUPPORTED");
      return;

    }

    if (call.method.equals("requestAuthorization")) {
      if (!platform.checkLocationServicesPermission()) {
        this.flutterResult = result;
        platform.requestAuthorization();
        return;
      }

      // Here, location services permission is granted.
      //
      // It's possible location permission was granted without going through
      // our onRequestPermissionsResult() - for example if a different flutter plugin
      // also requested location permissions, we could end up here with
      // checkLocationServicesPermission() returning true before we ever called requestAuthorization().
      //
      // In that case, we'll never get a notification posted to eventSinkLocationAuthorizationStatus
      //
      // So we could could have flutter code calling requestAuthorization here and expecting to see
      // a change in eventSinkLocationAuthorizationStatus but never receiving it.
      //
      // Ensure an ALLOWED status (possibly duplicate) is posted back.
      if (eventSinkLocationAuthorizationStatus != null) {
        eventSinkLocationAuthorizationStatus.success("ALLOWED");
      }

      result.success(true);
      return;
    }

    if (call.method.equals("openBluetoothSettings")) {
      if (!platform.checkBluetoothIfEnabled()) {
        this.flutterResultBluetooth = result;
        platform.openBluetoothSettings();
        return;
      }

      result.success(true);
      return;
    }

    if (call.method.equals("openLocationSettings")) {
      platform.openLocationSettings();
      result.success(true);
      return;
    }

    if (call.method.equals("openApplicationSettings")) {
      result.notImplemented();
      return;
    }

    if (call.method.equals("close")) {
      if (beaconManager != null) {
        beaconScanner.stopRanging();
        beaconManager.removeAllRangeNotifiers();
        beaconScanner.stopMonitoring();
        beaconManager.removeAllMonitorNotifiers();
        if (beaconManager.isBound(beaconScanner.beaconConsumer)) {
          beaconManager.unbind(beaconScanner.beaconConsumer);
        }
      }
      result.success(true);
      return;
    }

    if (call.method.equals("startBroadcast")) {
      beaconBroadcast.startBroadcast(call.arguments, result);
      return;
    }

    if (call.method.equals("stopBroadcast")) {
      beaconBroadcast.stopBroadcast(result);
      return;
    }

    if (call.method.equals("isBroadcasting")) {
      beaconBroadcast.isBroadcasting(result);
      return;
    }

    if (call.method.equals("isBroadcastSupported")) {
      result.success(platform.isBroadcastSupported());
      return;
    }

    result.notImplemented();
  }

  private void initializeAndCheck(Result result) {
    if (platform.checkLocationServicesPermission()
        && platform.checkBluetoothIfEnabled()
        && platform.checkLocationServicesIfEnabled()) {
      if (result != null) {
        result.success(true);
        return;
      }
    }

    flutterResult = result;
    if (!platform.checkBluetoothIfEnabled()) {
      platform.openBluetoothSettings();
      return;
    }

    if (!platform.checkLocationServicesPermission()) {
      platform.requestAuthorization();
      return;
    }

    if (!platform.checkLocationServicesIfEnabled()) {
      platform.openLocationSettings();
      return;
    }

    if (beaconManager != null && !beaconManager.isBound(beaconScanner.beaconConsumer)) {
      if (result != null) {
        this.flutterResult = result;
      }

      beaconManager.bind(beaconScanner.beaconConsumer);
      return;
    }

    if (result != null) {
      result.success(true);
    }
  }

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
      if (ContextCompat.checkSelfPermission(activityPluginBinding.getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(permission);
      }
    }

    if (!permissionsToRequest.isEmpty()) {
      ActivityCompat.requestPermissions(activityPluginBinding.getActivity(), permissionsToRequest.toArray(new String[0]), FlutterBeaconPlugin.REQUEST_PERMISSIONS);
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(activityPluginBinding.getActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(activityPluginBinding.getActivity(), new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
        } else {
          sendPermissionResult(true);
          LogManager.i(TAG, "All permissions are already granted");
//      Toast.makeText(this, "All permissions are already granted", Toast.LENGTH_SHORT).show();
        }
      } else {
        sendPermissionResult(true);
        LogManager.i(TAG, "All permissions are already granted");
//      Toast.makeText(this, "All permissions are already granted", Toast.LENGTH_SHORT).show();
      }


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
//    permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);

//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//      permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
//    }

//    Map<String, Boolean> permissionStatus = new HashMap<>();
//    for (String permission : permissions) {
//      boolean granted = ContextCompat.checkSelfPermission(getActivity(), permission) == PackageManager.PERMISSION_GRANTED;
//      permissionStatus.put(permission, granted);
//    }
//    return permissionStatus;

    boolean isAllGranted = true;
    for (String permission : permissions) {
      boolean isGranted = ContextCompat.checkSelfPermission(activityPluginBinding.getActivity(), permission) == PackageManager.PERMISSION_GRANTED;
      LogManager.i(TAG, "checkPermissions" + permission +":"+ isGranted);
      if (!isGranted) {
        isAllGranted = false;
      }
    }

    return isAllGranted;
  }


  private final EventChannel.StreamHandler locationAuthorizationStatusStreamHandler = new EventChannel.StreamHandler() {
    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
      eventSinkLocationAuthorizationStatus = events;
    }

    @Override
    public void onCancel(Object arguments) {
      eventSinkLocationAuthorizationStatus = null;
    }
  };

  // region ACTIVITY CALLBACK
  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == REQUEST_PERMISSIONS) {
      Map<String, Boolean> permissionResults = new HashMap<>();
      for (int i = 0; i < permissions.length; i++) {
        permissionResults.put(permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED);
      }

      // Optional: Do something with the results, like sending them to Flutter
      for (Map.Entry<String, Boolean> entry : permissionResults.entrySet()) {
        String permission = entry.getKey();
        boolean granted = entry.getValue();
        String message = granted ? permission + " granted" : permission + " denied";
        LogManager.i(TAG, message);
//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
      }

      // If all foreground permissions are granted, request background location permission
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(activityPluginBinding.getActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions(activityPluginBinding.getActivity(), new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
        } else {
          sendPermissionResult(true);
        }
      } else {
        sendPermissionResult(true);
      }
    }
////////
    if (requestCode != REQUEST_CODE_LOCATION) {
      return false;
    }

    boolean locationServiceAllowed = false;
    if (permissions.length > 0 && grantResults.length > 0) {
      String permission = permissions[0];
      if (!platform.shouldShowRequestPermissionRationale(permission)) {
        int grantResult = grantResults[0];
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
          //allowed
          locationServiceAllowed = true;
        }
        if (eventSinkLocationAuthorizationStatus != null) {
          // shouldShowRequestPermissionRationale = false, so if access wasn't granted, the user clicked DENY and checked DON'T SHOW AGAIN
          eventSinkLocationAuthorizationStatus.success(locationServiceAllowed ? "ALLOWED" : "DENIED");
        }
      }
      else {
        // shouldShowRequestPermissionRationale = true, so the user has clicked DENY but not DON'T SHOW AGAIN, we can possibly prompt again
        if (eventSinkLocationAuthorizationStatus != null) {
          eventSinkLocationAuthorizationStatus.success("NOT_DETERMINED");
        }
      }
    }
    else {
      // Permission request was cancelled (another requestPermission active, other interruptions), we can possibly prompt again
      if (eventSinkLocationAuthorizationStatus != null) {
        eventSinkLocationAuthorizationStatus.success("NOT_DETERMINED");
      }
    }

    if (flutterResult != null) {
      if (locationServiceAllowed) {
        flutterResult.success(true);
      } else {
        flutterResult.error("Beacon", "location services not allowed", null);
      }
      this.flutterResult = null;
    }

    return locationServiceAllowed;
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (requestCode == REQUEST_CODE_BLUETOOTH) {
      if (flutterResult != null) {
        if (resultCode == Activity.RESULT_OK) {
          flutterResult.success("STATE_ON");
        } else {
          flutterResult.error("BLUETOOTH_NOT_ENABLED", "Bluetooth enabling failed or was cancelled by user", null);
        }
        flutterResult = null;
      }
      return true;
    }
    return false;

  }

  private void sendPermissionResult(boolean allGranted) {
    if (flutterResult != null) {
      flutterResult.success(allGranted);
      flutterResult = null;
    }
  }

}
