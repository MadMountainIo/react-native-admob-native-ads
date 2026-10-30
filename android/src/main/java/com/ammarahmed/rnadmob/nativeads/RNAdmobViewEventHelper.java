package com.ammarahmed.rnadmob.nativeads;

import android.view.View;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.events.EventDispatcher;

public class RNAdmobViewEventHelper {
    private RNAdmobViewEventHelper() {
    }

    public static void sendEvent(View view, String eventName, @Nullable WritableMap eventData) {
        ReactContext reactContext = UIManagerHelper.getReactContext(view);
        int viewTag = view.getId();
        int surfaceId = UIManagerHelper.getSurfaceId(view);
        EventDispatcher eventDispatcher = UIManagerHelper.getEventDispatcher(reactContext);

        if (eventDispatcher == null) {
            return;
        }

        eventDispatcher.dispatchEvent(new RNAdmobViewEvent(surfaceId, viewTag, eventName, eventData));
    }
}
