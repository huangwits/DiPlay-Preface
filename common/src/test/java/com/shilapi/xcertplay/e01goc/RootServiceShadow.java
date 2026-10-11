package com.shilapi.xcertplay.e01goc;

import android.os.IBinder;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadows.ShadowServiceManager;

/** The default addService is a no-op; preserve standard services and add the OEM one. */
@Implements(className = "android.os.ServiceManager", isInAndroidSdk = false)
public class RootServiceShadow extends ShadowServiceManager {
    public static IBinder factory;

    @Implementation
    protected static IBinder getService(String name) {
        return "ExtraUtilsService".equals(name) ? factory : ShadowServiceManager.getService(name);
    }

    @Resetter
    public static void resetFactory() { factory = null; }
}
