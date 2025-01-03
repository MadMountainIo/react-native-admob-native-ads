//
//  OnUnifiedNativeAdLoadedListener.m
//  react-native-admob-native-ads
//
//  Created by Ali on 8/25/21.
//

#import <Foundation/Foundation.h>
#import "UnifiedNativeAdLoadedListener.h"
#import "RNAdMobUnifiedAdContainer.h"
#import "EventEmitter.h"
#import "CacheManager.h"

@implementation UnifiedNativeAdLoadedListener

-(instancetype) initWithRepo:(NSString *)repo nativeAds:(NSMutableArray<RNAdMobUnifiedAdContainer *> *) nativeAds tAds:(int)tAds {
    _repo = repo;
    _nativeAds = nativeAds;
    _totalAds = tAds;
    return self;
}

- (void)adLoader:(nonnull GADAdLoader *)adLoader didReceiveNativeAd:(nonnull GADNativeAd *)nativeAd {
    [self handleReceivedAd:nativeAd];
}

- (void)adLoader:(nonnull GADAdLoader *)adLoader didReceiveCustomNativeAd:(nonnull GADCustomNativeAd *)customNativeAd {
    [self handleReceivedCustomNativeAd:customNativeAd];
}

- (void)handleReceivedAd:(id)ad {
    long long time = (long long)([[NSDate date] timeIntervalSince1970] * 1000.0);
    if (self.nativeAds.count > _totalAds) {
        // remove oldest ad if it is full
        RNAdMobUnifiedAdContainer *toBeRemoved = nil;

        for (RNAdMobUnifiedAdContainer *adContainer in _nativeAds) {
            if (adContainer.loadTime < time && adContainer.references <= 0) {
                time = adContainer.loadTime;
                toBeRemoved = adContainer;
            }
        }
        if (toBeRemoved != nil) {
            toBeRemoved.unifiedNativeAd = nil;
            [self.nativeAds removeObject:toBeRemoved];
        }
    }
    RNAdMobUnifiedAdContainer *container = [[RNAdMobUnifiedAdContainer alloc] initWithAd:ad loadTime:time showCount:0];
    [self.nativeAds addObject:container];

    NSMutableDictionary* args = [[NSMutableDictionary alloc] init];
    [args setObject:[NSNumber numberWithInteger:_nativeAds.count] forKey:_repo];
    [EventEmitter.sharedInstance sendEvent:[CacheManager EVENT_AD_PRELOAD_LOADED:_repo] dict:args];
}

-(void)handleReceivedCustomNativeAd:(GADCustomNativeAd *)customNativeAd {
    long long time = (long long)([[NSDate date] timeIntervalSince1970] * 1000.0);
    if (self.nativeAds.count > _totalAds) {
        // remove oldest ad if it is full
        RNAdMobUnifiedAdContainer *toBeRemoved = nil;

        for (RNAdMobUnifiedAdContainer *adContainer in _nativeAds) {
            if (adContainer.loadTime < time && adContainer.references <= 0) {
                time = adContainer.loadTime;
                toBeRemoved = adContainer;
            }
        }
        if (toBeRemoved != nil) {
            toBeRemoved.unifiedNativeAd = nil;
            [self.nativeAds removeObject:toBeRemoved];
        }
    }
    RNAdMobUnifiedAdContainer *container = [[RNAdMobUnifiedAdContainer alloc] initWithCustomNativeAd:customNativeAd loadTime:time showCount:0];
    [self.nativeAds addObject:container];

    NSMutableDictionary* args = [[NSMutableDictionary alloc] init];
    [args setObject:[NSNumber numberWithInteger:_nativeAds.count] forKey:_repo];
    [EventEmitter.sharedInstance sendEvent:[CacheManager EVENT_AD_PRELOAD_LOADED:_repo] dict:args];
}

- (void)adLoader:(nonnull GADAdLoader *)adLoader didFailToReceiveAdWithError:(nonnull NSError *)error {

}

@end
