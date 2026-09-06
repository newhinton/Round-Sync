package ca.pkay.rcloneexplorer.Settings;

import static ca.pkay.rcloneexplorer.Activities.MainActivity.MAIN_ACTIVITY_START_EXPORT;
import static ca.pkay.rcloneexplorer.Activities.MainActivity.MAIN_ACTIVITY_START_IMPORT;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import ca.pkay.rcloneexplorer.Activities.MainActivity;
import ca.pkay.rcloneexplorer.R;
import es.dmoral.toasty.Toasty;

public class SettingsFragment extends Fragment {

    public final static int GENERAL_SETTINGS = 1;
    public final static int FILE_ACCESS_SETTINGS = 2;
    public final static int LOOK_AND_FEEL_SETTINGS = 3;
    public final static int LOGGING_SETTINGS = 4;
    public final static int NOTIFICATION_SETTINGS = 5;
    private OnSettingCategorySelectedListener clickListener;

    public interface OnSettingCategorySelectedListener {
        void onSettingCategoryClicked(int category);
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SettingsFragment() {
    }

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_fragment, container, false);
        setClickListeners(view);

        if (getActivity() != null) {
            getActivity().setTitle(getString(R.string.settings));
        }

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnSettingCategorySelectedListener) {
            clickListener = (OnSettingCategorySelectedListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement listener");
        }
    }

    private void setClickListeners(View view) {

        view.findViewById(R.id.general_settings).setOnClickListener(v -> clickListener.onSettingCategoryClicked(GENERAL_SETTINGS));

        view.findViewById(R.id.logging_settings).setOnClickListener(v -> clickListener.onSettingCategoryClicked(LOGGING_SETTINGS));

        view.findViewById(R.id.logging_settings).setOnClickListener(v -> clickListener.onSettingCategoryClicked(LOGGING_SETTINGS));

        view.findViewById(R.id.look_and_feel_settings).setOnClickListener(v -> clickListener.onSettingCategoryClicked(LOOK_AND_FEEL_SETTINGS));

        view.findViewById(R.id.notification_settings).setOnClickListener(v -> clickListener.onSettingCategoryClicked(NOTIFICATION_SETTINGS));

        view.findViewById(R.id.file_access_settings).setOnClickListener(v -> clickListener.onSettingCategoryClicked(FILE_ACCESS_SETTINGS));

        view.findViewById(R.id.importSettings).setOnClickListener(v -> startActivity(getImportIntent()));
        view.findViewById(R.id.exportSettings).setOnClickListener(v -> startActivity(getExportIntent()));

        view.findViewById(R.id.reset_app_settings).setOnClickListener(v -> showResetConfirmation());
    }

    private void showResetConfirmation() {
        Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.reset_app_confirm_title)
                .setMessage(R.string.reset_app_confirm_message)
                .setIcon(R.drawable.ic_delete_black)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> performReset())
                .show();
    }

    private void performReset() {
        Context context = requireContext();

        // 1. Delete rclone.conf
        File rcloneConf = new File(context.getFilesDir(), "rclone.conf");
        if (rcloneConf.exists()) {
            rcloneConf.delete();
        }

        // 2. Delete all files in filesDir (tokens, caches, etc.)
        deleteRecursive(context.getFilesDir());

        // 3. Delete cache
        deleteRecursive(context.getCacheDir());

        // 4. Delete external files (logs, etc.)
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles != null) {
            deleteRecursive(externalFiles);
        }

        // 5. Clear all shared preferences
        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .edit().clear().apply();

        // 6. Use ActivityManager to clear app data (this is the nuclear option)
        try {
            // Clear all app data through the system API
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                Toasty.success(context, context.getString(R.string.reset_app_success), Toast.LENGTH_SHORT, true).show();
                am.clearApplicationUserData();
                // clearApplicationUserData kills the process, so nothing below executes
            }
        } catch (Exception e) {
            // Fallback: just restart the app
            Toasty.success(context, context.getString(R.string.reset_app_success), Toast.LENGTH_SHORT, true).show();
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
            if (getActivity() != null) {
                getActivity().finishAffinity();
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private Intent getImportIntent() {
        Intent i = new Intent(this.getContext(), MainActivity.class);
        i.setAction(MAIN_ACTIVITY_START_IMPORT);
        return i;
    }

    private Intent getExportIntent() {
        Intent i = new Intent(this.getContext(), MainActivity.class);
        i.setAction(MAIN_ACTIVITY_START_EXPORT);
        return i;
    }
}
