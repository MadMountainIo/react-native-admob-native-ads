package com.ammarahmed.rnadmob.nativeads;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.CatalystInstance;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MediaContent;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.nativead.NativeCustomFormatAd;

import java.lang.reflect.Method;
import java.util.UUID;

public class RNAdmobNativeView extends LinearLayout {

    private final Runnable measureAndLayout = () -> {
        measure(
                MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
        layout(getLeft(), getTop(), getRight(), getBottom());
    };
    public int adRefreshInterval = 60000;
    protected @Nullable
    ReactContext mContext;
    NativeAdView nativeAdView;
    NativeAd nativeAd;
    NativeCustomFormatAd customFormatAd;
    VideoOptions.Builder videoOptions;
    AdManagerAdRequest.Builder adRequest;
    NativeAdOptions.Builder adOptions;
    AdLoader.Builder builder;
    AdLoader adLoader;
    RNAdmobMediaView mediaView;
    RNAdMobUnifiedAdContainer unifiedNativeAdContainer;
    CatalystInstance mCatalystInstance;
    private int mediaAspectRatio = 1;
    private boolean loadingAd = false;
    private Runnable retryRunnable;
    private String adRepo;
    private int adChoicesPlacement = 1;
    private boolean requestNonPersonalizedAdsOnly = false;
    private String admobAdUnitId = "";
    private Handler handler;
    private String[] customTemplateIds;

    AdListener adListener = new AdListener() {

        @Override
        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            super.onAdFailedToLoad(loadAdError);
            WritableMap event = Arguments.createMap();
            WritableMap error = Arguments.createMap();
            error.putString("message", loadAdError.getMessage());
            error.putInt("code", loadAdError.getCode());
            error.putString("domain", loadAdError.getDomain());
            event.putMap("error", error);
            loadingAd = false;
            if (adRepo != null) {
                 CacheManager.instance.detachAdListener(adRepo,adListener);
            }
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_FAILED_TO_LOAD, event);
        }

        @Override
        public void onAdClosed() {
            super.onAdClosed();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_CLOSED, null);
        }

        @Override
        public void onAdOpened() {
            super.onAdOpened();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_OPENED, null);
        }

        @Override
        public void onAdClicked() {
            super.onAdClicked();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_CLICKED, null);

        }

        @Override
        public void onAdLoaded() {
            super.onAdLoaded();
            if (adRepo != null) {
                CacheManager.instance.detachAdListener(adRepo,adListener);
                loadAd();
            }
            loadingAd = false;
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_LOADED, null);
        }

        @Override
        public void onAdImpression() {
            super.onAdImpression();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_IMPRESSION, null);
        }
    };

    NativeAd.OnNativeAdLoadedListener onNativeAdLoadedListener = new NativeAd.OnNativeAdLoadedListener() {
        @Override
        public void onNativeAdLoaded(NativeAd ad) {
            if (nativeAd != null) {
                nativeAd.destroy();
            }

            if (ad != null) {
                nativeAd = ad;
                setNativeAd();
            }
            loadingAd = false;
            setNativeAdToJS(ad);
        }
    };

    NativeCustomFormatAd.OnCustomFormatAdLoadedListener onCustomFormatAdLoadedListener = new NativeCustomFormatAd.OnCustomFormatAdLoadedListener() {
        @Override
        public void onCustomFormatAdLoaded(@NonNull NativeCustomFormatAd nativeCustomFormatAd) {

            if (customFormatAd != null) {
                customFormatAd.destroy();
            }

            if (nativeCustomFormatAd != null) {
                customFormatAd = nativeCustomFormatAd;
                setNativeAd();
            }

            loadingAd = false;

            setNativeAdToJS(nativeCustomFormatAd);
        }
    };

    NativeCustomFormatAd.OnCustomClickListener onCustomClickListener = new NativeCustomFormatAd.OnCustomClickListener() {
        @Override
        public void onCustomClick(@NonNull NativeCustomFormatAd nativeCustomFormatAd, @NonNull String s) {}
    };

    public RNAdmobNativeView(ReactContext context) {
        super(context);
        mContext = context;
        createView(context);
        handler = new Handler();
        mCatalystInstance = mContext.getCatalystInstance();
        setId(UUID.randomUUID().hashCode() + this.getId());
        videoOptions = new VideoOptions.Builder();
        adRequest = new AdManagerAdRequest.Builder();
        adOptions = new NativeAdOptions.Builder();
    }

    public void createView(Context context) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View viewRoot = layoutInflater.inflate(R.layout.rn_ad_unified_native_ad, this, true);
        nativeAdView = (NativeAdView) viewRoot.findViewById(R.id.native_ad_view);

    }

    public String getAdRepo() {
        return adRepo;
    }

    public void addMediaView(int id) {

        try {
            mediaView = (RNAdmobMediaView) nativeAdView.findViewById(id);
            if (mediaView != null) {
                if (nativeAd != null) {
                    nativeAd.getMediaContent().getVideoController().setVideoLifecycleCallbacks(mediaView.videoLifecycleCallbacks);
                }

                nativeAdView.setMediaView((MediaView) nativeAdView.findViewById(id));
                mediaView.requestLayout();

                setNativeAd();

            }
        } catch (Exception e) {

        }
    }

    private Method getDeclaredMethod(Object obj, String name) {
        try {
            return obj.getClass().getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setNativeAdToJS(NativeAd nativeAd) {

        try {
            WritableMap args = Arguments.createMap();

            args.putString("headline", nativeAd.getHeadline());
            args.putString("tagline", nativeAd.getBody());
            args.putString("advertiser", nativeAd.getAdvertiser());
            args.putString("callToAction", nativeAd.getCallToAction());
            args.putBoolean("video", nativeAd.getMediaContent().hasVideoContent());

            if (nativeAd.getPrice() != null) {
                args.putString("price", nativeAd.getPrice());
            }

            if (nativeAd.getStore() != null) {
                args.putString("store", nativeAd.getStore());
            }
            if (nativeAd.getStarRating() != null) {
                args.putInt("rating", nativeAd.getStarRating().intValue());
            }

            float aspectRatio = 1.0f;

            MediaContent mediaContent = nativeAd.getMediaContent();
            if (nativeAdView.getMediaView() != null) {
                nativeAdView.getMediaView().setMediaContent(nativeAd.getMediaContent());
            }


            if (mediaContent != null && null != getDeclaredMethod(mediaContent, "getAspectRatio")) {
                aspectRatio = nativeAd.getMediaContent().getAspectRatio();
                if (aspectRatio > 0) {
                    args.putString("aspectRatio", String.valueOf(aspectRatio));
                } else {
                    args.putString("aspectRatio", String.valueOf(1.0f));
                }

            } else {
                args.putString("aspectRatio", String.valueOf(1.0f));
            }

            args.putString("type", RNAdmobNativeViewManager.AD_TYPE_NATIVE);

            WritableArray images = new WritableNativeArray();

            if (nativeAd.getImages() != null && nativeAd.getImages().size() > 0) {

                for (int i = 0; i < nativeAd.getImages().size(); i++) {
                    WritableMap map = Arguments.createMap();
                    if (nativeAd.getImages().get(i) != null) {
                        map.putString("url", nativeAd.getImages().get(i).getUri().toString());
                        map.putInt("width", 0);
                        map.putInt("height", 0);
                        images.pushMap(map);
                    }
                }
            }

            if (images != null) {
                args.putArray("images", images);
            } else {
                args.putArray("images", null);
            }

            if (nativeAd.getIcon() != null) {
                if (nativeAd.getIcon().getUri() != null) {
                    args.putString("icon", nativeAd.getIcon().getUri().toString());
                } else {
                    args.putString("icon", "empty");
                }
            } else {
                args.putString("icon", "noicon");
            }

            sendEvent(RNAdmobNativeViewManager.EVENT_NATIVE_AD_LOADED,args);

        } catch (Exception e) {
        }
    }

    private void setNativeAdToJS(NativeCustomFormatAd nativeAd) {

        try {
            WritableMap args = Arguments.createMap();

            for (String assetName : nativeAd.getAvailableAssetNames()) {
                if (nativeAd.getText(assetName) != null) {
                    args.putString(assetName, nativeAd.getText(assetName).toString());
                } else if (nativeAd.getImage(assetName) != null) {
                    WritableMap imageMap = Arguments.createMap();
                    imageMap.putString("uri", nativeAd.getImage(assetName).getUri().toString());
                    imageMap.putInt("width", nativeAd.getImage(assetName).getDrawable().getIntrinsicWidth());
                    imageMap.putInt("height", nativeAd.getImage(assetName).getDrawable().getIntrinsicHeight());
                    imageMap.putDouble("scale", nativeAd.getImage(assetName).getScale());
                    args.putMap(assetName, imageMap);
                }
            }

            args.putBoolean("video", nativeAd.getMediaContent().hasVideoContent());
            float aspectRatio = 1.0f;

            MediaContent mediaContent = nativeAd.getMediaContent();
            if (nativeAdView.getMediaView() != null) {
                nativeAdView.getMediaView().setMediaContent(mediaContent);
            }

            if (mediaContent != null && getDeclaredMethod(mediaContent, "getAspectRatio") != null) {
                aspectRatio = nativeAd.getMediaContent().getAspectRatio();
                if (aspectRatio > 0) {
                    args.putString("aspectRatio", String.valueOf(aspectRatio));
                } else {
                    args.putString("aspectRatio", String.valueOf(1.0f));
                }
            } else {
                args.putString("aspectRatio", String.valueOf(1.0f));
            }

            args.putString("type", RNAdmobNativeViewManager.AD_TYPE_TEMPLATE);

            sendEvent(RNAdmobNativeViewManager.EVENT_CUSTOM_FORMAT_AD_LOADED,args);

        } catch (Exception e) {
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        loadingAd = false;
    }

    public void sendEvent(String name, @Nullable WritableMap event) {

        ReactContext reactContext = (ReactContext) mContext;
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                name,
                event);
    }

    public void loadAd() {
        if (adRepo != null) {
            getAdFromRepository();
        } else {
            try {
                if (loadingAd) return;
                loadingAd = true;
                adLoader.loadAd(adRequest.build());

            } catch (Exception e) {
                loadingAd = false;
            }
        }

    }

    public void recordImpression() {
        if (customFormatAd != null){
            customFormatAd.recordImpression();
        }
    }

    public void triggerClick() {
        if (customFormatAd != null) {
            customFormatAd.performClick("image");
        }
    }

    private void getAdFromRepository() {
        try {
            if (!CacheManager.instance.isRegistered(adRepo)) {
                if (adListener != null) {
                    adListener.onAdFailedToLoad(new LoadAdError(3, "The requested repo is not registered", "", null, null));
                }
            } else {

                CacheManager.instance.attachAdListener(adRepo, adListener);
                if (CacheManager.instance.numberOfAds(adRepo) != 0) {

                    unifiedNativeAdContainer = CacheManager.instance.getNativeAd(adRepo);

                    if (unifiedNativeAdContainer != null) {
                        nativeAd = unifiedNativeAdContainer.unifiedNativeAd;
                        if (nativeAd != null) {
                            nativeAdView.setNativeAd(nativeAd);
                            if (mediaView != null) {
                                nativeAdView.setMediaView(mediaView);
                                mediaView.requestLayout();
                                setNativeAd();
                            }
                            setNativeAdToJS(nativeAd);
                        } else {
                            customFormatAd = unifiedNativeAdContainer.unifiedCustomNativeAd;
                            if (customFormatAd != null) {
                                setNativeAd();
                                setNativeAdToJS(customFormatAd);
                            }
                        }
                    }
                } else {
                    if (!CacheManager.instance.isLoading(adRepo)) {
                        CacheManager.instance.requestAds(adRepo);
                    }
                }
            }

        } catch (Exception e) {
            adListener.onAdFailedToLoad(new LoadAdError(3, e.toString(), "", null, null));
        }
    }

    public void addNewView(View child, int index) {
        try {
            nativeAdView.addView(child, index);
            requestLayout();
            nativeAdView.requestLayout();
        } catch (Exception e) {

        }
    }

    @Override
    public void addView(View child) {
        super.addView(child);
        requestLayout();
    }

    public void setAdRefreshInterval(int interval) {
        adRefreshInterval = interval;
    }

    public void setAdRepository(String repo) {
        adRepo = repo;
    }

    public void setAdUnitId(String id) {
        admobAdUnitId = id;

        if (customTemplateIds == null) {
            return;
        }
        // We replace loadAdBuilder() with checkAndInitializeAdBuilder() to be sure its called after both adUnitId and customTemplateIds is set.
        // In rn < 0.72 it is setCustomTemplateIds -> setAdUnitId, but in 0.72 it is reversed (?).
        //  loadAdBuilder(); 

        // In case when adUnitIt is set after customTemplateIds
        checkAndInitializeAdBuilder();
    }

    public void setCustomTemplateIds(String[] ids) {
        customTemplateIds = ids;

        if (admobAdUnitId.isEmpty()) {
            return;
        }

        // In case when customTemplateIds is set after adUnitIt
        checkAndInitializeAdBuilder();
    }

    // It combines both setAdUnitId and setCustomTemplateIds requirements, and if all is set then it tries to load ad.
    private void checkAndInitializeAdBuilder() {
        if (admobAdUnitId != null && !admobAdUnitId.isEmpty() && customTemplateIds != null && customTemplateIds.length > 0) {
            loadAdBuilder();
        }
    }

    public void loadAdBuilder() {
        builder = new AdLoader.Builder(mContext, admobAdUnitId);
        builder.forNativeAd(onNativeAdLoadedListener);
        builder.withNativeAdOptions(adOptions.build());
        builder.withAdListener(adListener);

        if (customTemplateIds != null && customTemplateIds.length > 0) {
            for (int i = 0; i < customTemplateIds.length; i++) {
                builder.forCustomFormatAd(customTemplateIds[i], onCustomFormatAdLoadedListener, onCustomClickListener);
            }
        }

        adLoader = builder.build();
    }

    public void setAdChoicesPlacement(int location) {
        adChoicesPlacement = location;
        adOptions.setAdChoicesPlacement(adChoicesPlacement);
    }

    public void setRequestNonPersonalizedAdsOnly(boolean npa) {
        Utils.setRequestNonPersonalizedAdsOnly(npa, adRequest);

    }

    public void setMediaAspectRatio(int type) {
        mediaAspectRatio = type;
        adOptions.setMediaAspectRatio(mediaAspectRatio);
    }

    public void setNativeAd() {
        if (nativeAdView != null && nativeAd != null) {
            nativeAdView.setNativeAd(nativeAd);

            if (mediaView != null && nativeAdView.getMediaView() != null) {
                nativeAdView.getMediaView().setMediaContent(nativeAd.getMediaContent());
                if (nativeAd.getMediaContent().hasVideoContent()) {
                    mediaView.setVideoController(nativeAd.getMediaContent().getVideoController());
                    mediaView.setMedia(nativeAd.getMediaContent());
                }
            }

        }

        if (nativeAdView != null && customFormatAd != null) {
            if (customFormatAd.getDisplayOpenMeasurement() != null) {
                customFormatAd.getDisplayOpenMeasurement().setView(nativeAdView);
                customFormatAd.getDisplayOpenMeasurement().start();
            }
            if (mediaView != null && nativeAdView.getMediaView() != null) {
                nativeAdView.getMediaView().setMediaContent(customFormatAd.getMediaContent());

                if (customFormatAd.getMediaContent().hasVideoContent()) {
                    mediaView.setVideoController(customFormatAd.getMediaContent().getVideoController());
                    mediaView.setMedia(customFormatAd.getMediaContent());
                    mediaView.requestLayout();
                    nativeAdView.requestLayout();
                }
            }
        }
    }

    public void setVideoOptions(ReadableMap options) {
        Utils.setVideoOptions(options, videoOptions, adOptions);
    }

    public void setTargetingOptions(ReadableMap options) {
        Utils.setTargetingOptions(options, adRequest);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    public void removeHandler() {
        loadingAd = false;
        if (handler != null) {
            handler = null;
        }
    }
}
