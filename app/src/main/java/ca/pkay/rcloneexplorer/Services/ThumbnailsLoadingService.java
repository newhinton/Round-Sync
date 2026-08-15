package ca.pkay.rcloneexplorer.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.util.FLog;

public class ThumbnailsLoadingService extends Service {

    private static final String TAG = "ThumbnailsLoadingSvc";
    public static final String REMOTE_ARG = "ca.pkay.rcexplorer.ThumbnailsLoadingService.REMOTE_ARG";
    public static final String HIDDEN_PATH = "ca.pkay.rcexplorer.ThumbnailsLoadingService.HIDDEN_PATH";
    public static final String SERVER_PORT = "ca.pkay.rcexplorer.ThumbnailsLoadingService.PORT";
    private static final String CHANNEL_ID = "ca.pkay.rcloneexplorer.THUMBNAIL_CHANNEL";
    private static final int NOTIFICATION_ID = 45912;

    private Rclone rclone;
    private Process process;
    private Thread processThread;

    @Override
    public void onCreate() {
        super.onCreate();
        rclone = new Rclone(this);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.thumbnails),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_file)
                .setContentTitle(getString(R.string.thumbnails_loading_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOngoing(true);
        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }

        if (intent == null) {
            return START_NOT_STICKY;
        }

        RemoteItem remote = intent.getParcelableExtra(REMOTE_ARG);
        if (remote == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String hiddenPath = "/" + intent.getStringExtra(HIDDEN_PATH) + '/' + remote.getName();
        int serverPort = intent.getIntExtra(SERVER_PORT, 29179);
        FLog.d(TAG, "onStartCommand: hiddenPath=%s, port=%d", hiddenPath, serverPort);

        stopCurrentProcess();

        processThread = new Thread(() -> {
            try {
                process = rclone.serve(Rclone.SERVE_PROTOCOL_HTTP, serverPort, false, null, null, remote, "", hiddenPath);
                if (process != null) {
                    if (PreferenceManager.getDefaultSharedPreferences(ThumbnailsLoadingService.this)
                            .getBoolean(getString(R.string.pref_key_logs), false)) {
                        new Thread(() -> rclone.logErrorOutput(process)).start();
                    }
                    process.waitFor();
                }
            } catch (Exception e) {
                FLog.e(TAG, "error running thumbnail serve process", e);
            }
        });
        processThread.start();

        return START_NOT_STICKY;
    }

    private synchronized void stopCurrentProcess() {
        if (process != null) {
            try {
                process.destroy();
            } catch (Exception ignored) {}
            process = null;
        }
        if (processThread != null) {
            processThread.interrupt();
            processThread = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCurrentProcess();
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
