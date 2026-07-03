package com.jhotadhari.reactnative.mapsforge.vtm.ext.grib;

import androidx.annotation.NonNull;

import com.facebook.react.BaseReactPackage;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.module.model.ReactModuleInfoProvider;
import com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.modules.WeatherOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// "implements ReactPackage" is redundant (BaseReactPackage already implements it) but required
// for the react-native CLI's autolinking regex to detect this as the module's package class.
public class ExtGribPackage extends BaseReactPackage implements ReactPackage {

    @NonNull
    @Override
    public List<com.facebook.react.uimanager.ViewManager> createViewManagers(
        @NonNull ReactApplicationContext reactContext
    ) {
        // No custom views in this extension — only a TurboModule.
        return new ArrayList<>();
    }

    @Override
    public NativeModule getModule(
        @NonNull String s,
        @NonNull ReactApplicationContext reactApplicationContext
    ) {
        if (WeatherOverlay.NAME.equals(s)) {
            return new WeatherOverlay(reactApplicationContext);
        }
        return null;
    }

    @NonNull
    @Override
    public ReactModuleInfoProvider getReactModuleInfoProvider() {
        return new ReactModuleInfoProvider() {
            @NonNull
            @Override
            public Map<String, ReactModuleInfo> getReactModuleInfos() {
                Map<String, ReactModuleInfo> map = new HashMap<>();
                map.put(WeatherOverlay.NAME, new ReactModuleInfo(
                    WeatherOverlay.NAME,
                    WeatherOverlay.NAME,
                    false,  // canOverrideExistingModule
                    false,  // needsEagerInit
                    false,  // isCxxModule
                    true    // isTurboModule
                ));
                return map;
            }
        };
    }
}
