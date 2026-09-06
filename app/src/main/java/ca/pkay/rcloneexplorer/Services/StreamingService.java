package ca.pkay.rcloneexplorer.Services;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import ca.pkay.rcloneexplorer.BroadcastReceivers.ServeCancelAction;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.util.FLog;
import ca.pkay.rcloneexplorer.util.NotificationUtils;


public class StreamingService extends IntentService {

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
    private Process runningProcess;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.*
     */
    public StreamingService() {
        super("ca.pkay.rcexplorer.streamingservice");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setNotificationChannel();
        rclone = new Rclone(this);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        final String servePath = intent.getStringExtra(SERVE_PATH_ARG);
        final RemoteItem remote = intent.getParcelableExtra(REMOTE_ARG);
        final Boolean showNotificationText = intent.getBooleanExtra(SHOW_NOTIFICATION_TEXT, false);
        final int protocol = intent.getIntExtra(SERVE_PROTOCOL, SERVE_HTTP);
        final int port = intent.getIntExtra(SERVE_PORT, 8080);
        final Boolean allowRemoteAccess = intent.getBooleanExtra(ALLOW_REMOTE_ACCESS, false);
        final String authenticationUsername = intent.getStringExtra(AUTHENTICATION_USERNAME);
        final String authenticationPassword = intent.getStringExtra(AUTHENTICATION_PASSWORD);

        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE;
        }
        Intent foregroundIntent = new Intent(this, StreamingService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, foregroundIntent, flags);

        Intent cancelIntent = new Intent(this, ServeCancelAction.class);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_streaming)
                .setContentTitle(getString(R.string.streaming_service_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_cancel_download, getString(R.string.cancel), cancelPendingIntent);

        if (showNotificationText) {
            Uri uri = Uri.parse("http://127.0.0.1:" + port);
            Intent webPageIntent = new Intent(Intent.ACTION_VIEW, uri);
            webPageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent webPagePendingIntent = PendingIntent.getActivity(this, 0, webPageIntent, flags);
            builder.setContentIntent(webPagePendingIntent);
            builder.setContentText(getString(R.string.streaming_service_notification_content, port));
        }

        startForeground(PERSISTENT_NOTIFICATION_ID, builder.build());

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

        boolean exitedNormally = false;
        if (runningProcess != null) {
            try {
                runningProcess.waitFor();
                exitedNormally = true;
            } catch (InterruptedException e) {
                FLog.e(TAG, "onHandleIntent: error waiting for process", e);
            }
        }

        if (exitedNormally && runningProcess.exitValue() != 0) {
            rclone.logErrorOutput(runningProcess);
        }

        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != runningProcess) {
            runningProcess.destroy();
        }
    }

    private void setNotificationChannel() {
        NotificationUtils.createNotificationChannel(this,
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
                getString(R.string.streaming_service_notification_channel_description)
        );
    }
}
