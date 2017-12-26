package com.alibaba.android.binding.plugin.weex.internal;

import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import com.alibaba.android.binding.plugin.weex.ExpressionConstants;
import com.taobao.weex.utils.WXLogUtils;

import java.util.List;

class SensorManagerProxyImpl implements SensorManagerProxy {
    private final SensorManager mSensorManager;

    SensorManagerProxyImpl(SensorManager sensorManager) {
        mSensorManager = sensorManager;
    }

    @Override
    public boolean registerListener(SensorEventListener listener, int sensorType, int rate,
                                    Handler handler) {
        List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
        if (sensors.isEmpty()) {
            return false;
        }
        return mSensorManager.registerListener(listener, sensors.get(0), rate, handler);
    }

    @Override
    public void unregisterListener(SensorEventListener listener, int sensorType) {
        List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
        if (sensors.isEmpty()) {
            return;
        }
        try {
            mSensorManager.unregisterListener(listener, sensors.get(0));
        } catch (Throwable e) {
            // Suppress occasional exception on Digma iDxD* devices:
            // Receiver not registered: android.hardware.SystemSensorManager$1
            WXLogUtils.w(ExpressionConstants.TAG, "Failed to unregister device sensor " + sensors.get(0).getName());
        }
    }
}