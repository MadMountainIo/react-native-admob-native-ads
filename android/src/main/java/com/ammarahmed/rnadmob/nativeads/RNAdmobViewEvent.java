package com.ammarahmed.rnadmob.nativeads;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.Event;

public class RNAdmobViewEvent extends Event<RNAdmobViewEvent> {
    private final String mEventName;
    private final WritableMap mEventData;

    public RNAdmobViewEvent(int surfaceId, int viewTag, String eventName, @Nullable WritableMap eventData) {
        super(surfaceId, viewTag);
        mEventName = eventName;
        mEventData = eventData;
    }

    @Override
    public String getEventName() {
        return mEventName;
    }

    @Override
    protected WritableMap getEventData() {
        return mEventData;
    }
}
