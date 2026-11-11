package ca.pkay.rcloneexplorer.Activities;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;

import ca.pkay.rcloneexplorer.Settings.FileAccessPreferencesFragment;
import ca.pkay.rcloneexplorer.Settings.FileAccessSettingsFragment;
import ca.pkay.rcloneexplorer.Settings.LogPreferencesFragment;
import ca.pkay.rcloneexplorer.Settings.NotificationPreferencesFragment;
import ca.pkay.rcloneexplorer.Settings.SettingsFragment;
import ca.pkay.rcloneexplorer.Settings.GeneralPreferencesFragment;
import ca.pkay.rcloneexplorer.Settings.ThemingPreferencesFragment;
import ca.pkay.rcloneexplorer.util.ActivityHelper;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.RuntimeConfiguration;

public class SettingsActivity extends AppCompatActivity implements SettingsFragment.OnSettingCategorySelectedListener {

    public final static String THEME_CHANGED = "ca.pkay.rcexplorer.SettingsActivity.THEME_CHANGED";
    private final String SAVED_THEME_CHANGE = "ca.pkay.rcexplorer.SettingsActivity.OUTSTATE_THEME_CHANGED";
    private final String SAVED_FRAGMENT = "ca.pkay.rcexplorer.SettingsActivity.RESTORE_FRAGMENT";
    private boolean themeHasChanged;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(RuntimeConfiguration.attach(this, newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        ActivityHelper.applyTheme(this);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
            fragmentManager.popBackStack();
        }

        startSettingsFragment();

        if (savedInstanceState != null) {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(SAVED_FRAGMENT);
            if (fragment != null) {
                restoreFragment(fragment);
            }
        }

        themeHasChanged = savedInstanceState != null && savedInstanceState.getBoolean(SAVED_THEME_CHANGE, false);
        Intent returnData = new Intent();
        returnData.putExtra(THEME_CHANGED, themeHasChanged);
        setResult(RESULT_OK, returnData);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_THEME_CHANGE, themeHasChanged);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void restoreFragment(Fragment fragment) {
        if (fragment instanceof GeneralPreferencesFragment) {
            startGeneralSettingsFragment();
        } else if (fragment instanceof FileAccessSettingsFragment) {
            startFileAccessSettingsFragment();
        } else if (fragment instanceof ThemingPreferencesFragment) {
            startLookAndFeelSettingsFragment();
        } else if (fragment instanceof NotificationPreferencesFragment) {
            startNotificationSettingsFragment();
        } else if (fragment instanceof LogPreferencesFragment) {
            startLoggingSettingsActivity();
        }
    }

    private void startSettingsFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, SettingsFragment.newInstance());
        transaction.commit();
    }

    private void startGeneralSettingsFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, new GeneralPreferencesFragment(), SAVED_FRAGMENT);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void startFileAccessSettingsFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, FileAccessSettingsFragment.newInstance(), SAVED_FRAGMENT);
        //todo:  for now, use the old one until i can fully migrate the new one.
        //transaction.replace(R.id.flFragment, new FileAccessPreferencesFragment(), SAVED_FRAGMENT);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void startLookAndFeelSettingsFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, new ThemingPreferencesFragment(), SAVED_FRAGMENT);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void startNotificationSettingsFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, new NotificationPreferencesFragment(), SAVED_FRAGMENT);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void startLoggingSettingsActivity() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, new LogPreferencesFragment(), SAVED_FRAGMENT);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void onSettingCategoryClicked(int category) {
        switch (category) {
            case SettingsFragment.GENERAL_SETTINGS:
                startGeneralSettingsFragment();
                break;
            case SettingsFragment.FILE_ACCESS_SETTINGS:
                startFileAccessSettingsFragment();
                break;
            case SettingsFragment.LOOK_AND_FEEL_SETTINGS:
                startLookAndFeelSettingsFragment();
                break;
            case SettingsFragment.LOGGING_SETTINGS:
                startLoggingSettingsActivity();
                break;
            case SettingsFragment.NOTIFICATION_SETTINGS:
                startNotificationSettingsFragment();
                break;
        }
    }
}
