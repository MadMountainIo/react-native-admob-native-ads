package com.ammarahmed.rnadmob.nativeads;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeCustomFormatAd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RNAdMobUnifiedAdQueueWrapper {

    public String adUnitId;
    public String name;
    public Integer totalAds = 5;
    public long expirationInterval = 3600000; // in ms
    public int totalRetryCount = 10;
    public long retryDelay = 3000;
    private int retryCount = 0;
    public Boolean muted = true;
    public Boolean mediation = false;
    public List<RNAdMobUnifiedAdContainer> nativeAds;
    //AdListener attached to attachedAdListeners list,if they are waiting for load ads
    List<AdListener> attachedAdListeners = new ArrayList<>();
    Context mContext;
    int loadingAdRequestCount = 0;

    VideoOptions.Builder videoOptions;
    NativeAdOptions.Builder adOptions;
    AdListener adListener;
    private AdLoader adLoader;
    private AdManagerAdRequest.Builder adRequest;
    private UnifiedNativeAdLoadedListener unifiedNativeAdLoadedListener;
    private final Handler handler = new Handler();

    public RNAdMobUnifiedAdQueueWrapper(Context context, ReadableMap config, String repository) {
        mContext = context;
        adUnitId = config.getString("adUnitId");
        name = repository;
        videoOptions = new VideoOptions.Builder();
        adRequest = new AdManagerAdRequest.Builder();
        adOptions = new NativeAdOptions.Builder();

        adListener = new AdListener() {
            @Override
            public void onAdFailedToLoad(LoadAdError adError) {
                super.onAdFailedToLoad(adError);
                if (mediation) {
                    loadingAdRequestCount--;
                } else {
                    loadingAdRequestCount = 0;
                }
                if (loadingAdRequestCount > 0) {return;}//wait until all request failed

                boolean stopPreloading = false;
                switch (adError.getCode()) {
                    case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                    case AdRequest.ERROR_CODE_INVALID_REQUEST:
                        stopPreloading = true;
                }

                if (stopPreloading) {
                    WritableMap event = Arguments.createMap();
                    WritableMap error = Arguments.createMap();
                    error.putString("message", adError.getMessage());
                    error.putInt("code", adError.getCode());
                    error.putString("domain", adError.getDomain());
                    event.putMap("error", error);
                    EventEmitter.sendEvent((ReactContext) mContext, CacheManager.EVENT_AD_PRELOAD_ERROR + ":" + name, event);
                    notifyOnAdsLoadFailed(adError);
                    return;
                }

                if (retryCount >= totalRetryCount) {
                    WritableMap event = Arguments.createMap();
                    WritableMap error = Arguments.createMap();
                    error.putString("message", "reach maximum retry");
                    error.putInt("code", AdRequest.ERROR_CODE_INTERNAL_ERROR);
                    error.putString("domain", "");
                    event.putMap("error", error);
                    EventEmitter.sendEvent((ReactContext) mContext, CacheManager.EVENT_AD_PRELOAD_ERROR + ":" + name, event);
                    notifyOnAdsLoadFailed(adError);
                    return;
                }
                retryCount++;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        fillAds();
                    }
                }, retryDelay);

            }

            @Override
            public void onAdImpression() {
                super.onAdImpression();
                EventEmitter.sendEvent((ReactContext) mContext, CacheManager.EVENT_AD_IMPRESSION + ":" + name, null);

            }

            @Override
            public void onAdClosed() {
                super.onAdClosed();
                EventEmitter.sendEvent((ReactContext) mContext, CacheManager.EVENT_AD_CLOSED + ":" + name, null);

            }

            @Override
            public void onAdOpened() {
                super.onAdOpened();
                EventEmitter.sendEvent((ReactContext) mContext, CacheManager.EVENT_AD_OPEN + ":" + name, null);

            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                Log.d("RNADMOB", CacheManager.EVENT_AD_CLICKED + ":" + name);
                EventEmitter.sendEvent((ReactContext) mContext, CacheManager.EVENT_AD_CLICKED + ":" + name, null);

            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();

                retryCount = 0;
                if (mediation) {
                    loadingAdRequestCount--;
                } else {
                    loadingAdRequestCount = 0;
                }
                notifyOnAdsLoaded();
                if (loadingAdRequestCount == 0) { //wait until all requests finish
                    fillAds();//<-try to fill up if still not full
                }
            }
        };

        setConfiguration(config);

    }

    private void  notifyOnAdsLoadFailed(LoadAdError adError){
        //use Iterator to prevent concurrentModificationException in ArrayList
        AdListener[] array = attachedAdListeners.toArray(new AdListener[0]);
        for (AdListener item : array) {
            item.onAdFailedToLoad(adError);
        }
    }
    private void  notifyOnAdsLoaded(){
        //use Iterator to prevent concurrentModificationException in ArrayList
        AdListener[] array = attachedAdListeners.toArray(new AdListener[0]);
        for (AdListener item : array) {
            item.onAdLoaded();
        }
    }

    public void attachAdListener(AdListener listener) {
        attachedAdListeners.add(listener);
    }

    public void detachAdListener(AdListener listener) {
        attachedAdListeners.remove(listener);
    }

    public void setConfiguration(ReadableMap config) {
        if (config.hasKey("retryDelay")) {
            retryDelay = config.getInt("retryDelay");
        }
        if (config.hasKey("totalRetryCount")) {
            totalRetryCount = config.getInt("totalRetryCount");
        }
        if (config.hasKey("numOfAds")) {
            totalAds = config.getInt("numOfAds");
        }
        nativeAds = new ArrayList<RNAdMobUnifiedAdContainer>(totalAds);

        if (config.hasKey("mute")) {
            muted = config.getBoolean("mute");
        }
        if (config.hasKey("expirationPeriod")) {
            expirationInterval = config.getInt("expirationPeriod");
        }
        if (config.hasKey("mediationEnabled")) {
            mediation = config.getBoolean("mediationEnabled");
        }

        if (config.hasKey("adChoicesPlacement")) {
            adOptions.setAdChoicesPlacement(config.getInt("adChoicesPlacement"));
        }

        if (config.hasKey("requestNonPersonalizedAdsOnly")) {
            Utils.setRequestNonPersonalizedAdsOnly(config.getBoolean("requestNonPersonalizedAdsOnly"), adRequest);
        }

        if (config.hasKey("mediaAspectRatio")) {
            Utils.setMediaAspectRatio(config.getInt("mediaAspectRatio"), adOptions);
        }

        Utils.setVideoOptions(config.getMap("videoOptions"), videoOptions, adOptions);
        Utils.setTargetingOptions(config.getMap("targetingOptions"), adRequest);

        unifiedNativeAdLoadedListener = new UnifiedNativeAdLoadedListener(name, nativeAds,
                totalAds, mContext);
        AdLoader.Builder builder = new AdLoader.Builder(mContext, adUnitId);
        
        String[] customTemplateIds = new String[]{};
        if (config.hasKey("customTemplateIds")) {
            ReadableArray customTemplateIdsArray = config.getArray("customTemplateIds");
            customTemplateIds = new String[customTemplateIdsArray.size()];
            for (int i = 0; i < customTemplateIdsArray.size(); i++) {
                customTemplateIds[i] = customTemplateIdsArray.getString(i);
            }
        }

        if (customTemplateIds != null && customTemplateIds.length > 0) {
            for (String customTemplateId : customTemplateIds) {
                builder.forCustomFormatAd(customTemplateId, unifiedNativeAdLoadedListener, unifiedNativeAdLoadedListener);
            }
        }

        builder.forNativeAd(unifiedNativeAdLoadedListener);
        adLoader = builder.withAdListener(adListener).build();
    }

    public void fillAds() {
        int require2fill = totalAds - nativeAds.size();
        if (require2fill <= 0 || isLoading()) {
            return;
        }

        loadingAdRequestCount = require2fill;
        if (mediation) {
            for (int i = 0; i < require2fill; i++) {
                adLoader.loadAd(adRequest.build());
            }
        } else {
            adLoader.loadAds(adRequest.build(), require2fill);
        }
    }

    public RNAdMobUnifiedAdContainer getAd() {
        if (nativeAds.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        RNAdMobUnifiedAdContainer ad = null;
        Collections.sort(nativeAds, new RNAdMobUnifiedAdComparator());
        List<RNAdMobUnifiedAdContainer> discardItems = new ArrayList<>();
        for (RNAdMobUnifiedAdContainer item : nativeAds) {
            if ((now - item.loadTime) < expirationInterval) {
                ad = item;//acceptable ad found
                break;
            } else {
                if (item.references <= 0) {
                    discardItems.add(item);
                }
            }
        }
        for (RNAdMobUnifiedAdContainer item : discardItems) {
            item.unifiedNativeAd.destroy();
            nativeAds.remove(item);
        }

        fillAds();
        if (ad == null) {
            return null;
        }
        ad.showCount += 1;
        ad.references += 1;
        return ad;
    }

    public Boolean isLoading() {
        if (adLoader != null) {
            return adLoader.isLoading() || loadingAdRequestCount > 0;
        }
        return false;
    }

    public WritableMap hasAd() {
        WritableMap args = Arguments.createMap();
        args.putInt(name, nativeAds.size());
        return args;
    }
}
