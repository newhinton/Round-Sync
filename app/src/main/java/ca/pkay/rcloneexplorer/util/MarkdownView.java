package ca.pkay.rcloneexplorer.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.widget.Toast;

import org.markdownj.MarkdownProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import es.dmoral.toasty.Toasty;

public class MarkdownView extends WebView {

    private static final String TAG = "MarkdownView";

    public MarkdownView(Context context) {
        super(patchContext(context));
    }

    public MarkdownView(Context context, AttributeSet attrs) {
        super(patchContext(context), attrs);
    }

    public static void closeOnMissingWebView(Activity host, Exception exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("Failed to load WebView provider: No WebView installed")) {
            FLog.e(TAG, "onCreate: Failed to load WebView (Appcenter PUB #49494606u)", exception);
            Toasty.error(host.getApplicationContext(), "Install WebView and try again", Toast.LENGTH_LONG, true).show();
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
        new LoadMarkdownAsset(path, getContext(), this).execute();
    }

    private static class LoadMarkdownAsset extends AsyncTask<Void, Void, String> {

        private static final String TAG = "LoadMarkdownAsset";
        private final String assetName;
        private final Context context;
        private final WebView webView;

        public LoadMarkdownAsset(String assetName, Context context, WebView webView) {
            this.assetName = assetName;
            this.context = context.getApplicationContext();
            this.webView = webView;
        }

        @Override
        protected String doInBackground(Void... voids) {
            AssetManager assetManager = context.getAssets();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(assetManager.open(assetName)))) {
                StringBuilder markdown = new StringBuilder(4096);
                String line;
                while ((line = br.readLine()) != null) {
                    // Use \n as line seperator so that the processor does not
                    // have to replace this.
                    markdown.append(line).append('\n');
                }
                return new MarkdownProcessor().markdown(markdown.toString());
            } catch (IOException e) {
                FLog.e(TAG, "Could not load asset ", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String html) {
            if (null == html) {
                webView.loadUrl("about:blank");
            } else {
                webView.loadDataWithBaseURL("local://", html, "text/html", "UTF-8", null);
            }
        }
    }
}
