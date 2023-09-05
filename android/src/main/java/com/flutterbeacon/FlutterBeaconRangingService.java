// https://github.com/alann-maulana/flutter_beacon/tree/master/example
// https://github.com/Dev-hwang/flutter_foreground_task/blob/master/android/src/main/kotlin/com/pravera/flutter_foreground_task/service/ForegroundService.kt
// https://altbeacon.github.io/android-beacon-library/foreground-service.html
package com.flutterbeacon;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

class FlutterBeaconRangingService extends Service { // implements MethodChannel.MethodCallHandler
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

//    @Override
//    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {}
}

