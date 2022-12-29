package com.ammarahmed.rnadmob.nativeads;

import android.os.Bundle;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.nativead.NativeAdOptions;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static void setTargetingOptions(ReadableMap options, AdManagerAdRequest.Builder adRequest) {
        if (options == null) return;
        if (options.hasKey("customTargeting")) {
            ReadableMap customTargetingObject = options.getMap("customTargeting");
            CustomTargeting[] customTargetingArray = Utils.getCustomTargeting(customTargetingObject);

            for (int i = 0; i < customTargetingArray.length; i++) {
                if (customTargetingArray[i].values != null) {
                    adRequest.addCustomTargeting(customTargetingArray[i].key, customTargetingArray[i].values);
                } else {
                    adRequest.addCustomTargeting(customTargetingArray[i].key, customTargetingArray[i].value);
                }
            }
        }
        if (options.hasKey("categoryExclusions")) {
            ReadableArray categoryExclusions = options.getArray("categoryExclusions");
            for (int i = 0; i < categoryExclusions.size(); i++) {
                adRequest.addCategoryExclusion(categoryExclusions.getString(i));
            }
        }
        if (options.hasKey("publisherId")) {
            adRequest.setPublisherProvidedId(options.getString("publisherId"));
        }
        if (options.hasKey("requestAgent")) {
            adRequest.setRequestAgent(options.getString("requestAgent"));
        }
        if (options.hasKey("keywords")) {
            ReadableArray keywords = options.getArray("keywords");
            for (int i = 0; i < keywords.size(); i++) {
                adRequest.addKeyword(keywords.getString(i));
            }
        }
        if (options.hasKey("contentUrl")) {
            adRequest.setContentUrl(options.getString("contentUrl"));
        }
        if (options.hasKey("neighboringContentUrls")) {
            List list = Arguments.toList(options.getArray("neighboringContentUrls"));
            adRequest.setNeighboringContentUrls(list);
        }
    }

    public static void setVideoOptions(ReadableMap options, VideoOptions.Builder videoOptions, NativeAdOptions.Builder adOptions) {
        if (options == null) {
            adOptions.setVideoOptions(videoOptions.build());
            return;
        }
        if (options.hasKey("muted")) {
            videoOptions.setStartMuted(options.getBoolean("muted"));
        }

        if (options.hasKey("clickToExpand")) {
            videoOptions.setClickToExpandRequested(options.getBoolean("clickToExpand"));
        }

        if (options.hasKey("clickToExpand")) {
            videoOptions.setCustomControlsRequested(options.getBoolean("clickToExpand"));
        }

        adOptions.setVideoOptions(videoOptions.build());
    }

    public static void setMediaAspectRatio(int type, NativeAdOptions.Builder adOptions) {
        adOptions.setMediaAspectRatio(type);
    }

    public static void setMediationOptions(ReadableMap options, AdManagerAdRequest.Builder adRequest) {
        if (options == null) return;
    }

    public static void setRequestNonPersonalizedAdsOnly(boolean npa, AdManagerAdRequest.Builder adRequest) {
        Bundle extras = new Bundle();
        if (npa) {
            extras.putString("npa", "1");
        } else {
            extras.putString("npa", "0");
        }
        adRequest.addNetworkExtrasBundle(AdMobAdapter.class, extras);
    }

    public static CustomTargeting[] getCustomTargeting(ReadableMap customTargeting) {
        ArrayList<CustomTargeting> list = new ArrayList<CustomTargeting>();

        for (
                ReadableMapKeySetIterator it = customTargeting.keySetIterator();
                it.hasNextKey();
        ) {
            String key = it.nextKey();
            String value = null;

            ReadableType type = customTargeting.getType(key);
            switch (type) {
                case Null:
                    // skip null
//                    list.add(new CustomTargeting(key, null));
                    break;
                case Boolean:
                    list.add(new CustomTargeting(key, Boolean.toString(customTargeting.getBoolean(key))));
                    break;
                case Number:
                    list.add(new CustomTargeting(key, Double.toString(customTargeting.getDouble(key))));
                    break;
                case String:
                    list.add(new CustomTargeting(key, customTargeting.getString(key)));
                    break;
                case Map:
                    list.add(new CustomTargeting(key, fromObject(customTargeting.getMap(key))));
                    break;
                case Array:
                    list.add(new CustomTargeting(key, fromArray(customTargeting.getArray(key))));
                    break;
                default:
                    throw new IllegalArgumentException("Could not convert object with key: " + key + ".");
            }
        }

        CustomTargeting[] targetingList = list.toArray(new CustomTargeting[list.size()]);
        return targetingList;
    }

    private static List<String> fromObject(ReadableMap readableMap) {
        List<String> deconstructedList = new ArrayList<>();

        ReadableMapKeySetIterator iterator = readableMap.keySetIterator();

        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType type = readableMap.getType(key);

            switch (type) {
                case Null:
                    deconstructedList.add(null);
                    break;
                case Boolean:
                    deconstructedList.add(Boolean.toString(readableMap.getBoolean(key)));
                    break;
                case Number:
                    deconstructedList.add(Double.toString(readableMap.getDouble(key)));
                    break;
                case String:
                    deconstructedList.add(readableMap.getString(key));
                    break;
                case Map:
                    // skip second level maps
//                    deconstructedList.add(fromObject(readableMap.getMap(key)));
                    break;
                case Array:
                    // skip second level arrays
//                    deconstructedList.add(fromArray(readableMap.getArray(key)));
                    break;
            }
        }

        return deconstructedList;
    }

    private static List<String> fromArray(ReadableArray readableArray) {
        List<String> deconstructedList = new ArrayList<>(readableArray.size());

        for (int i = 0; i < readableArray.size(); i++) {
            ReadableType type = readableArray.getType(i);

            switch (type) {
                case Null:
                    deconstructedList.add(i, null);
                    break;
                case Boolean:
                    deconstructedList.add(i, Boolean.toString(readableArray.getBoolean(i)));
                    break;
                case Number:
                    deconstructedList.add(i, Double.toString(readableArray.getDouble(i)));
                    break;
                case String:
                    deconstructedList.add(i, readableArray.getString(i));
                    break;
                case Map:
                    // skip second level maps
//                    deconstructedList.add(i, fromObject(readableArray.getMap(i)));
                    break;
                case Array:
                    // skip second level arrays
//                    deconstructedList.add(i, fromArray(readableArray.getArray(i)));
                    break;
            }
        }

        return deconstructedList;
    }

}
