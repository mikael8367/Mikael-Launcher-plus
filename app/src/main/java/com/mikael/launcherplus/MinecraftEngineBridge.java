package com.mikael.launcherplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;

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
    public static final String EXTRA_REQUESTED_PROFILE_NAME =
            "com.mikael.launcherplus.extra.REQUESTED_PROFILE_NAME";

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

        // Never open a separately installed Amethyst app: its launch intent exposes
        // the Amethyst interface and does not guarantee the requested version will start.
        // Minecraft must be started by the engine integrated into this application.
        return false;
    }

    /**
     * Starts the installer path in the integrated Amethyst engine. A standalone UI-only
     * build deliberately returns false rather than pretending an external launcher has
     * understood the custom installation request.
     */
    public static boolean openVersionInstaller(Activity activity, String requestedVersion, String loader,
                                                         String requestedProfileName) {
        if (activity == null || !isValidVersion(requestedVersion) || !isValidLoader(loader)
                || !isValidProfileName(requestedProfileName)) return false;
        String ownPackage = activity.getPackageName();
        if (!"org.angelauramc.amethyst.debug".equals(ownPackage)
                && !"org.angelauramc.amethyst".equals(ownPackage)) {
            return false;
        }

        Intent bootstrap = new Intent();
        bootstrap.setClassName(ownPackage, "net.kdt.pojavlaunch.TestStorageActivity");
        bootstrap.putExtra(EXTRA_REQUESTED_VERSION, requestedVersion);
        bootstrap.putExtra(EXTRA_REQUESTED_LOADER, loader);
        bootstrap.putExtra(EXTRA_REQUESTED_PROFILE_NAME, requestedProfileName.trim());
        try {
            activity.startActivity(bootstrap);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private static boolean isValidProfileName(String value) {
        return value != null && value.trim().matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,31}")
                && !".".equals(value.trim()) && !"..".equals(value.trim());
    }

    private static boolean isValidLoader(String value) {
        if (value == null) return false;
        return "Vanilla".equals(value)
                || "Forge".equals(value)
                || "Fabric".equals(value)
                || "Quilt".equals(value)
                || "NeoForge".equals(value)
                || "OptiFine".equals(value)
                || "Forge + OptiFine".equals(value);
    }

    private static boolean isValidVersion(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,100}")
                && !".".equals(value) && !"..".equals(value);
    }
}
