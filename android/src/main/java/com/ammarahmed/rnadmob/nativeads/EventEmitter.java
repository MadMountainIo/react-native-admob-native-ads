package com.ammarahmed.rnadmob.nativeads;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;

public class EventEmitter {

    public static void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.emitDeviceEvent(eventName, params);
    }

}