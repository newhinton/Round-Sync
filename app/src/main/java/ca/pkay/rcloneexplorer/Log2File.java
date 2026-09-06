package ca.pkay.rcloneexplorer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import ca.pkay.rcloneexplorer.util.FLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log2File {

    private static final String TAG = "Log2File";
    private Context context;

    public Log2File(Context context) {
        this.context = context;
    }

    public void log(String message) {
        File path = context.getExternalFilesDir("logs");
        if (path == null) {
            return;
        }
        File logFile = new File(path, "log.txt");

        clearLogsIfTooBif(logFile);

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentDateTime = dateFormat.format(new Date());

        String logMessage = currentDateTime + " - " + message + "\n";

        new WriteToFile(logFile, logMessage).execute();
    }

    private void clearLogsIfTooBif(File logFile) {
        int fileSize = Integer.parseInt(String.valueOf(logFile.length() / 1024));
        if (fileSize > 10000) { // 10 MB
            logFile.delete();
        }
    }

    private static class WriteToFile extends AsyncTask<Void, Void, Void> {

        private File logFile;
        private String logMessage;

        WriteToFile(File logFile, String logMessage) {
            this.logFile = logFile;
            this.logMessage = logMessage;
        }
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                FileOutputStream stream = new FileOutputStream(logFile, true);
                stream.write(logMessage.getBytes());
                stream.close();
            } catch (IOException e) {
                FLog.e(TAG, "Could not write log file", e);
            }
            return null;
        }
    }
}
