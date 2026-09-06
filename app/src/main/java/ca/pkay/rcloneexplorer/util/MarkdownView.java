package ca.pkay.rcloneexplorer.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.widget.Toast;

import org.markdownj.MarkdownProcessor;

import ca.pkay.rcloneexplorer.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import es.dmoral.toasty.Toasty;

public class MarkdownView extends WebView {

    private static final String TAG = "MarkdownView";
    private final Executor ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MarkdownView-io");
        t.setDaemon(true);
        return t;
    });

    public MarkdownView(Context context) {
        super(patchContext(context));
    }

    public MarkdownView(Context context, AttributeSet attrs) {
        super(patchContext(context), attrs);
    }

    public static void closeOnMissingWebView(Activity host, Exception exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("Failed to load WebView provider: No WebView installed")) {
            FLog.e(TAG, "onCreate: Failed to load WebView (Appcenter PUB #49494606u)", exception);
            Toasty.error(host.getApplicationContext(), host.getString(R.string.install_webview_retry), Toast.LENGTH_LONG, true).show();
            host.finish();
        } else {
            throw new RuntimeException(exception);
        }
    }

    private static Context patchContext(Context context) {
        // TODO: Only affects appcompat 1.1.x, remove with 1.2.x
        //       https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview/58131421
        if (Build.VERSION.SDK_INT == 22 || Build.VERSION.SDK_INT == 23) {
            return context.createConfigurationContext(new Configuration());
        }
        return context;
    }

    public void loadAsset(String path) {
        // RC-39: migrated off AsyncTask to a background executor; the WebView is updated on the
        // UI thread via View.post(). LoadMarkdownAsset used to be a static nested AsyncTask.
        final String assetName = path;
        final WebView webView = this;
        ioExecutor.execute(() -> {
            Context context = webView.getContext();
            AssetManager assetManager = context.getAssets();
            String html;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(assetManager.open(assetName)))) {
                StringBuilder markdown = new StringBuilder(4096);
                String line;
                while ((line = br.readLine()) != null) {
                    markdown.append(line).append('\n');
                }
                html = new MarkdownProcessor().markdown(markdown.toString());
            } catch (IOException e) {
                FLog.e(TAG, "Could not load asset ", e);
                html = null;
            }
            final String result = html;
            webView.post(() -> {
                if (null == result) {
                    webView.loadUrl("about:blank");
                } else {
                    webView.loadDataWithBaseURL("local://", result, "text/html", "UTF-8", null);
                }
            });
        });
    }
}
