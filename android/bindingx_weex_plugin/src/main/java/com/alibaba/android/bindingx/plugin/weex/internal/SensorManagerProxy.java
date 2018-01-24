package com.alibaba.android.bindingx.plugin.weex.internal;

import android.hardware.SensorEventListener;
import android.os.Handler;

interface SensorManagerProxy {
    boolean registerListener(SensorEventListener listener, int sensorType, int rate,
                             Handler handler);

    void unregisterListener(SensorEventListener listener, int sensorType);
}