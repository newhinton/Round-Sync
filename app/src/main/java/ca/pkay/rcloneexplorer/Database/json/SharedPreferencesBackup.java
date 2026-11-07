package ca.pkay.rcloneexplorer.Database.json;

import static ca.pkay.rcloneexplorer.util.ActivityHelper.DARK;
import static ca.pkay.rcloneexplorer.util.ActivityHelper.FOLLOW_SYSTEM;
import static ca.pkay.rcloneexplorer.util.ActivityHelper.LIGHT;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import ca.pkay.rcloneexplorer.R;

public class SharedPreferencesBackup {

    public static String export(Context context) throws JSONException {

        //General Settings
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showThumbnails = sharedPreferences.getBoolean(context.getString(R.string.pref_key_show_thumbnails), false);
        boolean isWifiOnly = sharedPreferences.getBoolean(context.getString(R.string.pref_key_wifi_only_transfers), false);
        boolean allowWhileIdle = sharedPreferences.getBoolean(context.getString(R.string.shared_preferences_allow_sync_trigger_while_idle), false);
        boolean useProxy = sharedPreferences.getBoolean(context.getString(R.string.pref_key_use_proxy), false);
        String proxyProtocol = sharedPreferences.getString(context.getString(R.string.pref_key_proxy_protocol), "http");
        String proxyHost = sharedPreferences.getString(context.getString(R.string.pref_key_proxy_host), "localhost");
        int proxyPort = sharedPreferences.getInt(context.getString(R.string.pref_key_proxy_port), 8080);
        String proxyUser = sharedPreferences.getString(context.getString(R.string.pref_key_proxy_username), "");
        String transfers = sharedPreferences.getString(context.getString(R.string.pref_key_transfers), "4");

        // File Access
        boolean safEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_key_enable_saf), false);
        boolean refreshLaEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_key_refresh_local_aliases), true);
        boolean vcpEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_key_enable_vcp), false);
        boolean vcpDeclareLocal = sharedPreferences.getBoolean(context.getString(R.string.pref_key_vcp_declare_local), true);
        boolean vcpGrantAll = sharedPreferences.getBoolean(context.getString(R.string.pref_key_vcp_grant_all), false);

        // Look and Feel
        int oldTheme = sharedPreferences.getInt(context.getString(R.string.pref_key_theme_old), FOLLOW_SYSTEM);
        String newTheme = sharedPreferences.getString(context.getString(R.string.pref_key_theme), String.valueOf(oldTheme));
        boolean isWrapFilenames = sharedPreferences.getBoolean(context.getString(R.string.pref_key_wrap_filenames), true);

        // Notifications
        boolean appUpdates = sharedPreferences.getBoolean(context.getString(R.string.pref_key_app_updates), false);

        // Logging
        boolean useLogs = sharedPreferences.getBoolean(context.getString(R.string.pref_key_logs), false);


        JSONObject main = new JSONObject();

        main.put("showThumbnails", showThumbnails);
        main.put("isWifiOnly", isWifiOnly);
        main.put("allowWhileIdle", allowWhileIdle);
        main.put("useProxy", useProxy);
        main.put("proxyProtocol", proxyProtocol);
        main.put("proxyHost", proxyHost);
        main.put("proxyPort", proxyPort);
        main.put("proxyUser", proxyUser);
        main.put("transfers", transfers);
        main.put("safEnabled", safEnabled);
        main.put("refreshLaEnabled", refreshLaEnabled);
        main.put("vcpEnabled", vcpEnabled);
        main.put("vcpDeclareLocal", vcpDeclareLocal);
        main.put("vcpGrantAll", vcpGrantAll);
        main.put("isDarkTheme", newTheme);
        main.put("isWrapFilenames", isWrapFilenames);
        main.put("appUpdates", appUpdates);
        main.put("useLogs", useLogs);

        return main.toString();
    }

    /** Check everything that can fail during parsing before replacing a ZIP's configuration. */
    public static void validateJson(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        for (String key : new String[]{"showThumbnails", "isWifiOnly", "safEnabled", "refreshLaEnabled",
                "vcpEnabled", "vcpDeclareLocal", "isWrapFilenames", "appUpdates", "useLogs"}) {
            object.getBoolean(key);
        }
        Object theme = object.get("isDarkTheme");
        if (theme instanceof String) {
            try {
                Integer.parseInt((String) theme);
            } catch (NumberFormatException e) {
                throw new JSONException("Invalid theme");
            }
        } else if (!(theme instanceof Boolean)) {
            throw new JSONException("Invalid theme");
        }
    }

    public static void importJson(String json, Context context) throws JSONException {
        validateJson(json);
        JSONObject jsonObject = new JSONObject(json);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        //General Settings
        editor.putBoolean(context.getString(R.string.pref_key_show_thumbnails), jsonObject.getBoolean("showThumbnails"));
        editor.putBoolean(context.getString(R.string.pref_key_wifi_only_transfers), jsonObject.getBoolean("isWifiOnly"));
        // Never silently enable outbound proxy interception from an imported backup
        editor.putBoolean(context.getString(R.string.pref_key_use_proxy), false);
        editor.putBoolean(context.getString(R.string.shared_preferences_allow_sync_trigger_while_idle), jsonObject.optBoolean("allowWhileIdle", false));
        String importedProtocol = jsonObject.optString("proxyProtocol", "http");
        if (!"http".equals(importedProtocol) && !"https".equals(importedProtocol) && !"socks5".equals(importedProtocol)) {
            importedProtocol = "http";
        }
        int importedPort = jsonObject.optInt("proxyPort", 8080);
        if (importedPort < 1 || importedPort > 65535) {
            importedPort = 8080;
        }
        editor.putString(context.getString(R.string.pref_key_proxy_protocol), importedProtocol);
        editor.putString(context.getString(R.string.pref_key_proxy_host), jsonObject.optString("proxyHost", "localhost"));
        editor.putInt(context.getString(R.string.pref_key_proxy_port), importedPort);
        editor.putString(context.getString(R.string.pref_key_proxy_username), jsonObject.optString("proxyUser", ""));
        editor.putString(context.getString(R.string.pref_key_transfers), jsonObject.optString("transfers", "4"));

        // File Access
        editor.putBoolean(context.getString(R.string.pref_key_enable_saf), jsonObject.getBoolean("safEnabled"));
        editor.putBoolean(context.getString(R.string.pref_key_refresh_local_aliases), jsonObject.getBoolean("refreshLaEnabled"));
        editor.putBoolean(context.getString(R.string.pref_key_enable_vcp), jsonObject.getBoolean("vcpEnabled"));
        editor.putBoolean(context.getString(R.string.pref_key_vcp_declare_local), jsonObject.getBoolean("vcpDeclareLocal"));
        // Never silently enable grant-all URI permissions from an imported backup
        editor.putBoolean(context.getString(R.string.pref_key_vcp_grant_all), false);

        // Look and Feel
        // The type changed. So we try to use boolean first, and if it fails we use the proper int
        Object darkTheme = jsonObject.get("isDarkTheme");
        int valueForTheme;
        if (darkTheme instanceof String) {
            valueForTheme = Integer.parseInt((String) darkTheme);
        } else {
           if((boolean) darkTheme) {
               valueForTheme = DARK;
           } else {
               valueForTheme = LIGHT;
           }
            editor.putString(context.getString(R.string.pref_key_theme), String.valueOf(valueForTheme));
        }

        editor.putString(context.getString(R.string.pref_key_theme), String.valueOf(valueForTheme));

        editor.putBoolean(context.getString(R.string.pref_key_wrap_filenames), jsonObject.getBoolean("isWrapFilenames"));

        // Notifications
        editor.putBoolean(context.getString(R.string.pref_key_app_updates), jsonObject.getBoolean("appUpdates"));

        // Logging
        editor.putBoolean(context.getString(R.string.pref_key_logs), jsonObject.getBoolean("useLogs"));

        editor.apply();
    }

}
