package ca.pkay.rcloneexplorer.util;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import androidx.annotation.Nullable;

/**
 * Keeps the CPU and Wi-Fi radio awake for the duration of a long-running rclone transfer.
 *
 * <p>A foreground service keeps the process ranked but does <b>not</b> prevent the device from
 * entering Doze with the screen off. Without these locks, large uploads/downloads stall until
 * the next Doze maintenance window, which destroys throughput. See the transmission-speed audit
 * (P0 item 2).
 */
public final class TransferLocks {

    private final PowerManager.WakeLock wakeLock;
    private final WifiManager.WifiLock wifiLock;

    private TransferLocks(PowerManager.WakeLock wakeLock, WifiManager.WifiLock wifiLock) {
        this.wakeLock = wakeLock;
        this.wifiLock = wifiLock;
    }

    /**
     * Acquires a {@link PowerManager#PARTIAL_WAKE_LOCK} plus a high-performance
     * {@link WifiManager.WifiLock}. Both are held until {@link #release()} (callers MUST call it
     * from a {@code finally} block). Returns {@code null} if the locks could not be acquired
     * (the caller may still proceed; transfers will simply be subject to Doze throttling).
     */
    @Nullable
    public static TransferLocks acquire(Context context, String tag) {
        PowerManager.WakeLock wake = null;
        WifiManager.WifiLock wifi = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CloudBridge:" + tag);
                wake.setReferenceCounted(false);
                wake.acquire();
            }
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CloudBridge:" + tag);
                wifi.setReferenceCounted(false);
                wifi.acquire();
            }
            return new TransferLocks(wake, wifi);
        } catch (RuntimeException e) {
            FLog.e("TransferLocks", "acquire failed, releasing partial state", e);
            if (wake != null && wake.isHeld()) {
                try { wake.release(); } catch (RuntimeException ignored) { }
            }
            if (wifi != null && wifi.isHeld()) {
                try { wifi.release(); } catch (RuntimeException ignored) { }
            }
            return null;
        }
    }

    /** Releases both locks if held. Safe to call from a {@code finally} block. */
    public void release() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (RuntimeException e) {
            FLog.e("TransferLocks", "release wakeLock", e);
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        } catch (RuntimeException e) {
            FLog.e("TransferLocks", "release wifiLock", e);
        }
    }
}
