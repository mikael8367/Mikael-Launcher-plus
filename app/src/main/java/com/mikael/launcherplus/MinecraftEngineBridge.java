package com.mikael.launcherplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Safe handoff to an installed Android Minecraft Java engine.
 *
 * This does not embed the engine in this APK. It opens the companion engine's
 * own launcher, where account, version downloads, loader installation and launch
 * are handled by that engine.
 */
public final class MinecraftEngineBridge {
    private static final String[] ENGINE_PACKAGES = {
            "org.angelauramc.amethyst.debug",
            "org.angelauramc.amethyst"
    };

    private MinecraftEngineBridge() { }

    public static boolean openInstalledEngine(Activity activity) {
        PackageManager packageManager = activity.getPackageManager();
        for (String packageName : ENGINE_PACKAGES) {
            try {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) continue;
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launchIntent);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Continue checking the next explicitly supported engine package.
            }
        }
        return false;
    }
}
