package ca.pkay.rcloneexplorer.Dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;

public class ServeDialog extends DialogFragment {

    private Context context;
    private Callback callback;
    private RadioGroup protocol;
    private CheckBox allowRemoteAccess;
    private EditText user;
    private EditText password;
    private AlertDialog dialog;

    public interface Callback {
        void onServeOptionsSelected(int protocol, boolean allowRemoteAccess, String user, String password);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (getParentFragment() != null) {
            callback = (Callback) getParentFragment();
        }


        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, R.style.RoundedCornersDialog);
        LayoutInflater layoutInflater = ((FragmentActivity)context).getLayoutInflater();
        View view = layoutInflater.inflate(R.layout.dialog_serve, null);

        protocol = view.findViewById(R.id.radio_group_protocol);
        allowRemoteAccess = view.findViewById(R.id.checkbox_allow_remote_access);
        user = view.findViewById(R.id.edit_text_user);
        password = view.findViewById(R.id.edit_text_password);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (pref.contains(getString(R.string.pref_choice_serve_dialog_allow_ext))) {
            boolean checked = pref.getBoolean(getString(R.string.pref_choice_serve_dialog_allow_ext), false);
            allowRemoteAccess.setChecked(checked);
        }

        ((TextInputLayout) view.findViewById(R.id.text_input_layout_user)).setHint("Username");
        ((TextInputLayout) view.findViewById(R.id.text_input_layout_password)).setHint("Password");

        builder.setTitle(R.string.serve_dialog_title);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> sendCallback());
        builder.setNegativeButton(R.string.cancel, null);
        builder.setView(view);

        ((CheckBox) view.findViewById(R.id.checkbox_allow_remote_access)).setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                Snackbar.make(btn, R.string.serve_dialog_remote_notice_enabled, Snackbar.LENGTH_LONG)
                        .show();
            } else {
                Snackbar.make(btn, R.string.serve_dialog_remote_notice_disabled, Snackbar.LENGTH_LONG)
                        .show();
            }
            updateOkButtonState();
        });

        TextWatcher credentialsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateOkButtonState();
            }
        };
        user.addTextChangedListener(credentialsWatcher);
        password.addTextChangedListener(credentialsWatcher);

        dialog = builder.show();
        updateOkButtonState();
        return dialog;
    }

    private void updateOkButtonState() {
        if (dialog == null) {
            return;
        }
        boolean canProceed = !allowRemoteAccess.isChecked()
                || (!user.getText().toString().trim().isEmpty()
                && !password.getText().toString().trim().isEmpty());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(canProceed);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("protocol", protocol.getCheckedRadioButtonId());
        outState.putBoolean("allowRemoteAccess", allowRemoteAccess.isChecked());
        if (!user.getText().toString().trim().isEmpty()) {
            outState.putString("user", user.getText().toString());
        }
        if (!password.getText().toString().trim().isEmpty()) {
            outState.putString("password", password.getText().toString());
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }

        allowRemoteAccess.setChecked(savedInstanceState.getBoolean("allowRemoteAccess", false));
        String savedUser = savedInstanceState.getString("user");
        if (savedUser != null) {
            user.setText(savedUser);
        }

        String savedPassword = savedInstanceState.getString("password");
        if (savedPassword != null) {
            password.setText(savedPassword);
        }

        int savedProtocol = savedInstanceState.getInt("protocol", -1);
        if (savedProtocol == R.id.radio_http || savedProtocol == R.id.radio_dlna
                || savedProtocol == R.id.radio_webdav || savedProtocol == R.id.radio_ftp) {
            protocol.check(savedProtocol);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;

        if (context instanceof Callback) {
            callback = (Callback) context;
        }
    }

    private void sendCallback() {
        int selectedProtocolId = protocol.getCheckedRadioButtonId();
        int selectedProtocol;
        switch (selectedProtocolId) {
            case R.id.radio_ftp:
                selectedProtocol = Rclone.SERVE_PROTOCOL_FTP;
                break;
            case R.id.radio_dlna:
                selectedProtocol = Rclone.SERVE_PROTOCOL_DLNA;
                break;
            case R.id.radio_webdav:
                selectedProtocol = Rclone.SERVE_PROTOCOL_WEBDAV;
                break;
            case R.id.radio_http:
            default:
                selectedProtocol = Rclone.SERVE_PROTOCOL_HTTP;
                break;
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(getString(R.string.pref_choice_serve_dialog_allow_ext), allowRemoteAccess.isChecked())
                .apply();

        callback.onServeOptionsSelected(selectedProtocol, allowRemoteAccess.isChecked(), user.getText().toString(), password.getText().toString());
    }
}
