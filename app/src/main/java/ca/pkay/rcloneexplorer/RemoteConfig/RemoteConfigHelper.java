package ca.pkay.rcloneexplorer.RemoteConfig;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import es.dmoral.toasty.Toasty;
import io.github.x0b.safdav.SafAccessProvider;
import io.github.x0b.safdav.file.SafConstants;

import java.util.ArrayList;

public class RemoteConfigHelper {

    public static String getRemotePath(String path, RemoteItem selectedRemote) {
        String remotePath;
        if (selectedRemote.isRemoteType(RemoteItem.LOCAL)) {
            if (path.equals("//" + selectedRemote.getName())) {
                remotePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
            } else {
                remotePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
            }
        } else {
            if (path.equals("//" + selectedRemote.getName())) {
                remotePath = selectedRemote.getName() + ":";
            } else {
                remotePath = selectedRemote.getName() + ":" + path;
            }
        }
        return remotePath;
    }

    public static void updateAndWait(Context context, ArrayList<String> options) {
        Rclone rclone = new Rclone(context);
        Process process = rclone.configUpdate(options);
        rcloneRun(process, context, options);
    }

    public static void setupAndWait(Context context, ArrayList<String> options) {
        Rclone rclone = new Rclone(context);
        Process process = rclone.configCreate(options);
        rcloneRun(process, context, options);
    }

    private static void rcloneRun(Process process, Context context, ArrayList<String> options) {
        if (null == process) {
            Toasty.error(context, context.getString(R.string.error_creating_remote), Toast.LENGTH_SHORT, true).show();
            return;
        }
        
        // Capture stderr in background thread for debugging
        final StringBuilder errorOutput = new StringBuilder();
        Thread errorReader = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            } catch (java.io.IOException e) {
                Log.e("RemoteConfigHelper", "Error reading stderr", e);
            }
        });
        errorReader.start();
        
        int exitCode;
        while (true) {
            try {
                exitCode = process.waitFor();
                break;
            } catch (InterruptedException e) {
                try {
                    exitCode = process.exitValue();
                    break;
                } catch (IllegalStateException ignored) {}
            }
        }
        
        // Wait for error reader to finish
        try {
            errorReader.join(1000);
        } catch (InterruptedException ignored) {}
        
        if (0 != exitCode) {
            Log.e("RemoteConfigHelper", "rclone config create failed with exit code: " + exitCode);
            Log.e("RemoteConfigHelper", "rclone stderr: " + errorOutput.toString());
            Toasty.error(context, context.getString(R.string.error_creating_remote), Toast.LENGTH_SHORT, true).show();
        } else {
            Toasty.success(context, context.getString(R.string.remote_creation_success), Toast.LENGTH_SHORT, true).show();
        }
    }

    public static void enableSaf(Context context) {
        String user = SafAccessProvider.getUser(context);
        String pass = SafAccessProvider.getPassword(context);
        ArrayList<String> options = new ArrayList<>();
        options.add(SafConstants.SAF_REMOTE_NAME);
        options.add("webdav");
        options.add("url");
        options.add(SafConstants.SAF_REMOTE_URL);
        options.add("user");
        options.add(user);
        options.add("pass");
        options.add(pass);
        setupAndWait(context, options);
    }
}
