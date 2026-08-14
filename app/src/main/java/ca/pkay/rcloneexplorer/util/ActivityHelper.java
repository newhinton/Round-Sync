package ca.pkay.rcloneexplorer.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import ca.pkay.rcloneexplorer.R;
import es.dmoral.toasty.Toasty;

public class ActivityHelper {

    private static final String TAG = "ActivityHelper";

    public static final int FOLLOW_SYSTEM = 0;
    public static final int DARK = 1;
    public static final int LIGHT = 2;



    @SuppressLint("CheckResult")
    public static void tryStartActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showErrorToast(context, intent);
        }
    }

    @SuppressLint("CheckResult")
    public static void tryStartActivityForResult(Fragment fragment, Intent intent, int requestCode) {
        try {
            if (!fragment.isAdded()) {
                FLog.w(TAG, "tryStartActivityForResult: fragment is not attached, can't start");
                return;
            }
            fragment.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            showErrorToast(fragment.getContext(), intent);
        }
    }

    public static void tryStartActivityForResult(Activity activity, Intent intent, int requestCode) {
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            showErrorToast(activity, intent);
        }
    }

    private static boolean requiresDocumentsUI(Intent intent) {
        if (null == intent || null == intent.getAction()) {
            return false;
        }
        switch (intent.getAction()) {
            case Intent.ACTION_GET_CONTENT:
            case Intent.ACTION_CREATE_DOCUMENT:
            case Intent.ACTION_OPEN_DOCUMENT:
            case Intent.ACTION_OPEN_DOCUMENT_TREE:
                return true;
            default:
                return false;
        }
    }

    private static void showErrorToast(final Context context, Intent intent) {
        Looper main = Looper.getMainLooper();
        String errorMessage = requiresDocumentsUI(intent) ?
                context.getString(R.string.documentsui_missing) :
                context.getString(R.string.no_app_found_for_this_link);
        Toast errorToast = Toasty.error(context, errorMessage, Toast.LENGTH_LONG, true);
        if (main.equals(Looper.myLooper())) {
            errorToast.show();
        } else {
            new Handler(main).post(errorToast::show);
        }
    }

    public static void tryStartService(@NonNull Context context, @NonNull Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            FLog.e(TAG, "Host context state is invalid, not starting service", e);
        }
    }

    public static void applyTheme(Activity activity) {
        applyDarkMode(activity);
    }

    public static boolean isDarkTheme(Context context) {
        String mode = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.pref_key_theme), String.valueOf(FOLLOW_SYSTEM));

        if(mode.equals(String.valueOf(LIGHT))) {
            return false;
        } else if(mode.equals(String.valueOf(DARK))) {
            return true;
        } else {
            Configuration config = context.getApplicationContext().getResources().getConfiguration();
            int currentNightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            switch (currentNightMode) {
                case Configuration.UI_MODE_NIGHT_NO:
                    return false;
                case Configuration.UI_MODE_NIGHT_YES:
                    return true;
            }
        }
        return false;
    }

    public static void applyDarkMode(Activity activity) {
        String mode = PreferenceManager.getDefaultSharedPreferences(activity).getString(activity.getString(R.string.pref_key_theme), String.valueOf(FOLLOW_SYSTEM));

        if(mode.equals(String.valueOf(LIGHT))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if(mode.equals(String.valueOf(DARK))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
