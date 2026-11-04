package ca.pkay.rcloneexplorer.Activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import ca.pkay.rcloneexplorer.AppShortcutsHelper;

/** Receives only the app's notification action and forwards it with a fresh process token. */
public class InternxtReauthEntryActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri data = getIntent().getData();
        String remoteName = data == null ? null : data.getLastPathSegment();
        if (remoteName != null) {
            Intent intent = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.MAIN_ACTIVITY_START_REAUTH)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(AppShortcutsHelper.APP_SHORTCUT_REMOTE_NAME, remoteName)
                    .putExtra(MainActivity.EXTRA_INTERNAL_ACTION_TOKEN, MainActivity.INTERNAL_ACTION_TOKEN);
            startActivity(intent);
        }
        finish();
    }
}
