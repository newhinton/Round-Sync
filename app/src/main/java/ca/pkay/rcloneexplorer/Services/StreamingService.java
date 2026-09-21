package ca.pkay.rcloneexplorer.Services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import ca.pkay.rcloneexplorer.BroadcastReceivers.ServeCancelAction;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.util.FLog;
import ca.pkay.rcloneexplorer.util.NotificationUtils;


public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    public static final String SERVE_PATH_ARG = "ca.pkay.rcexplorer.streaming_service.arg1";
    public static final String REMOTE_ARG = "ca.pkay.rcexplorer.streaming_service.arg2";
    public static final String SHOW_NOTIFICATION_TEXT = "ca.pkay.rcexplorer.streaming_service.arg3";
    public static final String SERVE_PORT = "ca.pkay.rcexplorer.streaming_service.arg4";
    public static final String SERVE_PROTOCOL = "ca.pkay.rcexplorer.serve_protocol";
    public static final String ALLOW_REMOTE_ACCESS = "ca.pkay.rcexplorer.allow_remote_access";
    public static final String AUTHENTICATION_USERNAME = "ca.pkay.rcexplorer.username";
    public static final String AUTHENTICATION_PASSWORD = "ca.pkay.rcexplorer.password";
    public static final int SERVE_HTTP = 11;
    public static final int SERVE_WEBDAV = 12;
    public static final int SERVE_FTP = 13;
    public static final int SERVE_DLNA = 14;
    private final String CHANNEL_ID = "ca.pkay.rcexplorer.streaming_channel";
    private final String CHANNEL_NAME = "Streaming service";
    private final int PERSISTENT_NOTIFICATION_ID = 179;
    private Rclone rclone;
    private volatile Process runningProcess;
    private Thread serveThread;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationUtils.createNotificationChannel(this,
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
                getString(R.string.streaming_service_notification_channel_description)
        );
        rclone = new Rclone(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        final String servePath = intent.getStringExtra(SERVE_PATH_ARG);
        final RemoteItem remote = intent.getParcelableExtra(REMOTE_ARG);
        final boolean showNotificationText = intent.getBooleanExtra(SHOW_NOTIFICATION_TEXT, false);
        final int protocol = intent.getIntExtra(SERVE_PROTOCOL, SERVE_HTTP);
        final int port = intent.getIntExtra(SERVE_PORT, 8080);
        final boolean allowRemoteAccess = intent.getBooleanExtra(ALLOW_REMOTE_ACCESS, false);
        final String authenticationUsername = intent.getStringExtra(AUTHENTICATION_USERNAME);
        final String authenticationPassword = intent.getStringExtra(AUTHENTICATION_PASSWORD);

        int pendingFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags = PendingIntent.FLAG_IMMUTABLE;
        }

        Intent cancelIntent = new Intent(this, ServeCancelAction.class);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_streaming)
                .setContentTitle(getString(R.string.streaming_service_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_cancel_download, getString(R.string.cancel), cancelPendingIntent);

        if (showNotificationText) {
            Uri uri = Uri.parse("http://127.0.0.1:" + port);
            Intent webPageIntent = new Intent(Intent.ACTION_VIEW, uri);
            webPageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent webPagePendingIntent = PendingIntent.getActivity(this, 0, webPageIntent, pendingFlags);
            builder.setContentIntent(webPagePendingIntent);
            builder.setContentText(getString(R.string.streaming_service_notification_content, port));
        }

        // Promote to foreground immediately on the main thread — this is critical on GrapheneOS
        // and Android 12+ where the 5-second window is strictly enforced. IntentService called
        // startForeground() from a background thread, causing the service to be killed before
        // promotion on stricter OS variants.
        startForeground(PERSISTENT_NOTIFICATION_ID, builder.build());

        stopServeProcess();

        serveThread = new Thread(() -> {
            switch (protocol) {
                case SERVE_FTP:
                    runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_FTP, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath);
                    break;
                case SERVE_WEBDAV:
                    runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_WEBDAV, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath);
                    break;
                case SERVE_DLNA:
                    runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_DLNA, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath);
                    break;
                case SERVE_HTTP:
                default:
                    runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_HTTP, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath);
                    break;
            }

            if (runningProcess != null) {
                try {
                    runningProcess.waitFor();
                } catch (InterruptedException e) {
                    FLog.e(TAG, "serve thread interrupted while waiting for process", e);
                    Thread.currentThread().interrupt();
                }
            }

            if (runningProcess != null && runningProcess.exitValue() != 0) {
                rclone.logErrorOutput(runningProcess);
            }

            stopForeground(true);
            stopSelf();
        });
        serveThread.start();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopServeProcess();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopServeProcess() {
        if (runningProcess != null) {
            runningProcess.destroy();
            runningProcess = null;
        }
        if (serveThread != null) {
            serveThread.interrupt();
            serveThread = null;
        }
    }
}
