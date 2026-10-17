package com.mikael.launcherplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Handoff to the real Amethyst/Pojav Minecraft Java engine.
 *
 * The integrated engine build forwards the requested version through its storage
 * bootstrap and asks the native launcher to start that version. A standalone UI-only
 * build cannot run Minecraft by itself; it needs this engine integrated or installed.
 */
public final class MinecraftEngineBridge {
    public static final String EXTRA_REQUESTED_VERSION =
            "com.mikael.launcherplus.extra.REQUESTED_VERSION";
    public static final String EXTRA_REQUESTED_LOADER =
            "com.mikael.launcherplus.extra.REQUESTED_LOADER";

    private static final String[] ENGINE_PACKAGES = {
            "org.angelauramc.amethyst.debug",
            "org.angelauramc.amethyst"
    };

    private MinecraftEngineBridge() { }

    public static boolean openInstalledEngine(Activity activity, String requestedVersion) {
        if (activity == null || !isValidVersion(requestedVersion)) return false;

        String ownPackage = activity.getPackageName();
        if ("org.angelauramc.amethyst.debug".equals(ownPackage)
                || "org.angelauramc.amethyst".equals(ownPackage)) {
            // The integrated APK must run the storage/bootstrap activity first.
            Intent bootstrap = new Intent();
            bootstrap.setClassName(ownPackage, "net.kdt.pojavlaunch.TestStorageActivity");
            bootstrap.putExtra(EXTRA_REQUESTED_VERSION, requestedVersion);
            try {
                activity.startActivity(bootstrap);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                return false;
            }
        }

        PackageManager packageManager = activity.getPackageManager();
        for (String packageName : ENGINE_PACKAGES) {
            try {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) continue;
                launchIntent.putExtra(EXTRA_REQUESTED_VERSION, requestedVersion);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launchIntent);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Continue checking the next explicitly supported engine package.
            }
        }
        return false;
    }

    /**
     * Starts the installer path in the integrated Amethyst engine. A standalone UI-only
     * build deliberately returns false rather than pretending an external launcher has
     * understood the custom installation request.
     */
    public static boolean openVersionInstaller(Activity activity, String requestedVersion, String loader) {
        if (activity == null || !isValidVersion(requestedVersion) || !isValidLoader(loader)) return false;
        String ownPackage = activity.getPackageName();
        if (!"org.angelauramc.amethyst.debug".equals(ownPackage)
                && !"org.angelauramc.amethyst".equals(ownPackage)) {
            return false;
        }

        Intent bootstrap = new Intent();
        bootstrap.setClassName(ownPackage, "net.kdt.pojavlaunch.TestStorageActivity");
        bootstrap.putExtra(EXTRA_REQUESTED_VERSION, requestedVersion);
        bootstrap.putExtra(EXTRA_REQUESTED_LOADER, loader);
        try {
            activity.startActivity(bootstrap);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private static boolean isValidLoader(String value) {
        if (value == null) return false;
        return "Vanilla".equals(value)
                || "Forge".equals(value)
                || "Fabric".equals(value)
                || "OptiFine".equals(value)
                || "Forge + OptiFine".equals(value);
    }

    private static boolean isValidVersion(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,100}")
                && !".".equals(value) && !"..".equals(value);
    }
}
