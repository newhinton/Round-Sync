package ca.pkay.rcloneexplorer.RemoteConfig;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import ca.pkay.rcloneexplorer.InteractiveRunner;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.util.FLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides utility methods for authorization of OAuth remotes
 */
public class OauthHelper {

    private static final String TAG = "OAuthHelper";
    private static final String regex = "go to the following link: ([^\\s]+)";
    private static final OauthProcessToken oauthProcessToken = new OauthProcessToken();

    // Since OAuth always blocks port 53682, only a single authentication
    // attempt is allowed at a time.
    static class OauthProcessToken {

        private volatile WeakReference<UrlAuthThread> threadAuthThread;
        private volatile WeakReference<InteractiveRunner> runner;

        public synchronized boolean acquire(UrlAuthThread controlThread) {
            boolean oldAttemptStopped = forceRelease();
            this.threadAuthThread = new WeakReference<>(controlThread);
            return oldAttemptStopped;
        }

        public synchronized boolean acquire(InteractiveRunner runner) {
            boolean oldAttemptStopped = forceRelease();
            this.runner = new WeakReference<>(runner);
            return oldAttemptStopped;
        }

        public synchronized boolean forceRelease() {
            UrlAuthThread oldThread = threadAuthThread != null ? threadAuthThread.get() : null;
            InteractiveRunner oldRunner = runner != null ? runner.get() : null;
            boolean killed = false;

            if (oldThread != null) {
                if (!oldThread.isStopped()) {
                    FLog.d(TAG, "Removing old auth attempt");
                    oldThread.forceStop();
                    killed = true;
                }
            }

            if (oldRunner != null) {
                FLog.d(TAG, "Removing old re-auth attempt");
                oldRunner.forceStop();
                killed = true;
            }

            return killed;
        }
    }

    /**
     * Ensure that an OAuth attempt can be made.
     */
    public static void registerRunner(InteractiveRunner runner) {
        oauthProcessToken.forceRelease();
        oauthProcessToken.acquire(runner);
    }

    /**
     * Save the options in the rclone config file and start the OAuth authentication process
     * @param options a list of rclone options, starting with remote name and type
     * @param rclone the rclone to use
     * @param context a context to start
     * @return true if successful
     **/
    public static boolean createOptionsWithOauth(ArrayList<String> options, Rclone rclone, Context context) {
        // Since authorization uses a fixed port, shut down previous attempt.
        oauthProcessToken.forceRelease();

        Process process = rclone.configCreate(options);
        if (null == process) {
            return false;
        }
        UrlAuthThread currentAuth = new OauthHelper.UrlAuthThread(process, context);
        oauthProcessToken.acquire(currentAuth);
        currentAuth.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            FLog.d(TAG, "Auth stopped by process interrupt");
            currentAuth.forceStop();
        }
        return 0 == process.exitValue();
    }

    /**
     * Monitor a rclone process for an authentication url and launch a browser
     * tab for the user. Note: this consumes the processes InputStream (stdout).
     */
    public static class UrlAuthThread extends Thread {
        private static final Pattern pattern = Pattern.compile(regex, 0);

        private static final String TAG = "UrlAuthThread";
        private final Process process;
        private final Context context;
        private volatile boolean stopped = false;

        public UrlAuthThread(Process process, Context context) {
            this.process = process;
            this.context = context;
        }

        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while (null != (line = br.readLine())) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String url = matcher.group(1);
                        if (url != null) {
                            launchBrowser(context, url);
                        }

                        // Do NOT break here, or the stream will be closed.
                        // When rclone then tries to write to the stream, it will receive SIGPIPE
                        // and rclone will exit confused why it can't just output its log messages.
                        // Instead, wait for rclone to close the stream.
                    }
                }
            } catch (IOException e) {
                if (stopped) {
                    FLog.v(TAG, "Authentication attempt stopped");
                    return;
                }
                stopped = true;
                FLog.e(TAG, "doInBackground: could not read auth url", e);
                process.destroy();
            }
        }

        public void forceStop() {
            stopped = true;
            process.destroy();
        }

        public boolean isStopped() {
            return stopped;
        }
    }

    static void launchBrowser(@NonNull Context context, @NonNull String url) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        try {
            customTabsIntent.launchUrl(context, Uri.parse(url));
        } catch (SecurityException e) {
            // This happens if a buggy third party component is registered for
            // browser intents with a non-exported activity.
            // TODO: Fix this for Android TV
            FLog.e(TAG, "Could not launch browser", e);
        }
    }

    private static class OauthAction implements InteractiveRunner.Action {

        private static final Pattern pattern = Pattern.compile(regex, 0);
        private Context context;

        public OauthAction(Context context) {
            this.context = context;
        }

        @Override
        public void onTrigger(String cliBuffer) {
            Matcher matcher = pattern.matcher(cliBuffer);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (url != null) {
                    launchBrowser(context, url);
                }
            } else {
                FLog.w(TAG, "onTrigger: could not extract auth URL from buffer: %s", cliBuffer);
            }
        }

        @Override
        public String getInput() {
            return "";
        }
    }

    public static class InitOauthStep extends InteractiveRunner.Step {
        private static final String TRIGGER = "Log in and authorize rclone for access";

        /**
         * An OAuth step that launches a browser. ATTENTION: must be registered
         * with {@link OauthHelper} to allow removal in case the port is needed.
         * @param context
         */
        public InitOauthStep(Context context) {
            super(TRIGGER,  new OauthHelper.OauthAction(context));
        }
    }

    public static class OauthFinishStep extends InteractiveRunner.Step {

        private static final String TRIGGER = "Got code\n";

        public OauthFinishStep() {
            super(TRIGGER, InteractiveRunner.Step.ENDS_WITH, InteractiveRunner.Step.STDOUT,
                    new InteractiveRunner.StringAction(""));
        }

        @Override
        public long getTimeout() {
            return 5 * 60 * 1000L;
        }
    }

    /**
     * An action that shows a dialog promp for Internxt 2FA code.
     * Uses a CountDownLatch to block until user enters the code.
     */
    public static class InternxtTwoFactorAction implements InteractiveRunner.Action {
        private final Context context;
        private String twoFactorCode = "";
        private final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        public InternxtTwoFactorAction(Context context) {
            this.context = context;
        }

        @Override
        public void onTrigger(String cliBuffer) {
            // Show dialog on UI thread and wait for input
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> showTwoFactorDialog());

            // Wait for user to enter the code (timeout after 5 minutes)
            try {
                latch.await(5, java.util.concurrent.TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                FLog.e(TAG, "2FA wait interrupted", e);
            }
        }

        private void showTwoFactorDialog() {
            androidx.appcompat.app.AlertDialog.Builder builder = 
                new androidx.appcompat.app.AlertDialog.Builder(context);
            builder.setTitle("Internxt Two-Factor Authentication");
            builder.setMessage("Enter your 2FA code from your authenticator app:");

            final android.widget.EditText input = new android.widget.EditText(context);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setHint("6-digit code");
            
            android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding, padding, 0);
            layout.addView(input);
            builder.setView(layout);

            builder.setPositiveButton("Submit", (dialog, which) -> {
                twoFactorCode = input.getText().toString().trim();
                latch.countDown();
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                twoFactorCode = "";
                latch.countDown();
            });
            
            builder.setCancelable(false);
            builder.show();
        }

        @Override
        public String getInput() {
            return twoFactorCode;
        }
    }

    /**
     * A step that triggers on Internxt 2FA prompt and shows a dialog for code input.
     * Trigger pattern matches exact text from rclone internxt.go source.
     */
    public static class InternxtTwoFactorStep extends InteractiveRunner.Step {
        // Exact prompt from rclone internxt.go: fs.ConfigInput("2fa", "config_2fa", "Two-factor authentication code")
        private static final String TRIGGER = "Two-factor authentication code";

        public InternxtTwoFactorStep(Context context) {
            super(TRIGGER, InteractiveRunner.Step.CONTAINS, InteractiveRunner.Step.INTERLEAVED,
                    new InternxtTwoFactorAction(context));
        }

        @Override
        public long getTimeout() {
            // Wait up to 2 minutes for the 2FA prompt to appear
            return 2 * 60 * 1000L;
        }
    }

    /**
     * A step for Internxt that just waits for the config to complete.
     * Triggers when we see successful completion indicators.
     */
    public static class InternxtFinishStep extends InteractiveRunner.Step {
        private static final String TRIGGER = "Keep this";

        public InternxtFinishStep() {
            super(TRIGGER, InteractiveRunner.Step.CONTAINS, InteractiveRunner.Step.INTERLEAVED,
                    new InteractiveRunner.StringAction("y"));
        }

        @Override
        public long getTimeout() {
            // Wait up to 2 minutes for login to complete
            return 2 * 60 * 1000L;
        }
    }
}
