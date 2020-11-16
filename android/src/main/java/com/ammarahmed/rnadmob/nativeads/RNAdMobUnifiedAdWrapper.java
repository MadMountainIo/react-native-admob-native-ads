package com.ammarahmed.rnadmob.nativeads;

import android.content.Context;
import android.os.Bundle;
import android.util.Pair;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.formats.NativeAdOptions;
import com.google.android.gms.ads.formats.UnifiedNativeAd;

import java.lang.Long;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class RNAdMobUnifiedAdWrapper {

    public String adUnitId;
    public Boolean npa = true;
    public Integer totalAds = 5;
    public long expirationInterval = 60*60*1000; // in ms
    public Boolean muted = true;
    private final AdLoader adLoader;
    private AdRequest adRequest;
    private final onUnifiedNativeAdLoadedListener unifiedNativeAdLoadedListener;
    AdListener attachedAdListener;
    public Stack<Pair<Long, UnifiedNativeAd>> mutedAds= new Stack<>(); // every entry is => time of loading => ad loaded
    public Stack<Pair<Long, UnifiedNativeAd>> unMutedAds= new Stack<>(); // every entry is => time of loading => ad loaded
    public Map<Boolean, Stack<Pair<Long, UnifiedNativeAd>>> nativeAdsMap = new HashMap<>();
    Context mContext;

    private final AdListener adListener = new AdListener() {
        @Override
        public void onAdFailedToLoad(LoadAdError adError) {
            super.onAdFailedToLoad(adError);
            String errorMessage = "";
            boolean stopPreloading = false;
            switch (adError.getCode()) {
                case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                    stopPreloading = true;
                    errorMessage = "Internal error, an invalid response was received from the ad server.";
                    break;
                case AdRequest.ERROR_CODE_INVALID_REQUEST:
                    stopPreloading = true;
                    errorMessage = "Invalid ad request, possibly an incorrect ad unit ID was given.";
                    break;
                case AdRequest.ERROR_CODE_NETWORK_ERROR:
                    errorMessage = "The ad request was unsuccessful due to network connectivity.";
                    break;
                case AdRequest.ERROR_CODE_NO_FILL:
                    errorMessage = "The ad request was successful, but no ad was returned due to lack of ad inventory.";
                    break;
            }
            if (attachedAdListener == null) {
                if (stopPreloading) {
                    WritableMap event = Arguments.createMap();
                    WritableMap error = Arguments.createMap();
                    error.putString("errorMessage", adError.getMessage());
                    error.putString("message", errorMessage);
                    error.putInt("code", adError.getCode());
                    error.putString("responseInfo", adError.getResponseInfo().toString());
                    event.putMap("error", error);
                    EventEmitter.sendEvent((ReactContext) mContext , Constants.EVENT_AD_PRELOAD_ERROR, event);
                }
                return;
            };
            attachedAdListener.onAdFailedToLoad(adError);
        }

        @Override
        public void onAdClosed() {
            super.onAdClosed();
            if (attachedAdListener == null) return;
            attachedAdListener.onAdClosed();
        }

        @Override
        public void onAdOpened() {
            super.onAdOpened();
            if (attachedAdListener == null) return;
            attachedAdListener.onAdOpened();
        }

        @Override
        public void onAdClicked() {
            super.onAdClicked();
            if (attachedAdListener == null) return;
            attachedAdListener.onAdClicked();

        }

        @Override
        public void onAdLoaded() {
            super.onAdLoaded();
            if (attachedAdListener == null) return;
            attachedAdListener.onAdLoaded();
//            if (nativeAds.size() == 1) {
//                attachedAdListener.onAdLoaded();
//            }
        }

        @Override
        public void onAdImpression() {
            super.onAdImpression();
            if (attachedAdListener == null) return;
            attachedAdListener.onAdImpression();
        }

        @Override
        public void onAdLeftApplication() {
            super.onAdLeftApplication();
            if (attachedAdListener == null) return;
            attachedAdListener.onAdLeftApplication();
        }
    };

    public void attachAdListener(AdListener listener) {
        attachedAdListener = listener;
    }

    public void detachAdListener() {
        attachedAdListener = null;
    }

    public RNAdMobUnifiedAdWrapper(Context context, ReadableMap config){
        mContext = context;
        adUnitId = config.getString("adUnitId");
        nativeAdsMap.put(true, mutedAds);
        nativeAdsMap.put(false, unMutedAds);


        if (config.hasKey("numOfAds")){
            totalAds = config.getInt("numOfAds");
        }
        if (config.hasKey("mute")){
            muted = config.getBoolean("mute");
        }
        if (config.hasKey("requestNonPersonalizedAdsOnly")) {
            npa = config.getBoolean("requestNonPersonalizedAdsOnly");
            if (config.hasKey("requestNonPersonalizedAdsOnly")) {
                Bundle extras = new Bundle();
                extras.putString("npa", config.getBoolean("requestNonPersonalizedAdsOnly") ? "1" : "0");
                adRequest = new AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter.class, extras).build();
            } else {
                adRequest = new AdRequest.Builder().build();
            }
        }
        unifiedNativeAdLoadedListener = new onUnifiedNativeAdLoadedListener(adUnitId, nativeAdsMap, context);
        AdLoader.Builder builder = new AdLoader.Builder(context, adUnitId);
        builder.forUnifiedNativeAd(unifiedNativeAdLoadedListener);
        VideoOptions videoOptions = new VideoOptions.Builder()
                .setStartMuted(muted)
                .build();

        NativeAdOptions adOptions = new NativeAdOptions.Builder()
                .setVideoOptions(videoOptions)
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT) // todo:: get from config
                .build();
        builder.withNativeAdOptions(adOptions);

        adLoader = builder.withAdListener(adListener).build();
    }

    public void loadAds(){
//        adLoader.loadAds(adRequest, totalAds);
        for (int i = 0; i<totalAds; i++){
            adLoader.loadAd(adRequest);
        }
    }

    public void loadAd(){
        adLoader.loadAd(adRequest);
        fillAd();
    }

    public void fillAd(){
        if (!isLoading()){
            for (int i = 0; i<(totalAds-nativeAdsMap.get(muted).size()); i++){
                adLoader.loadAd(adRequest);
            }
        }
    }

    public UnifiedNativeAd getAd(){
        long now = System.currentTimeMillis();
        Pair<Long, UnifiedNativeAd> ad;
        while (true){
            if (nativeAdsMap.get(muted).size() > 0){
                ad = nativeAdsMap.get(muted).pop();
//                fillAd();
                if ((ad.first - now) < expirationInterval){
                    break;
                }
            }else{
                return null;
            }
        }
        fillAd();
        return ad.second;
    }

    public Boolean isLoading(){
        if (adLoader != null){
            return adLoader.isLoading();
        }
        return false;
    }

    public WritableMap hasLoadedAd(){
        WritableMap args = Arguments.createMap();
        args.putInt("muted", nativeAdsMap.get(true).size());
        args.putInt("unMuted", nativeAdsMap.get(false).size());
        return args;
    }
}
