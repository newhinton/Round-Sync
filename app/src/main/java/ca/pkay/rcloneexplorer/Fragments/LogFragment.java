package ca.pkay.rcloneexplorer.Fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;

import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.RecyclerViewAdapters.LogRecyclerViewAdapter;
import ca.pkay.rcloneexplorer.util.SyncLog;
import es.dmoral.toasty.Toasty;
import jp.wasabeef.recyclerview.animators.LandingAnimator;

public class LogFragment extends Fragment {

    private View fragmentView;
    private RecyclerView recyclerView;
    private View emptyLogView;
    private LogRecyclerViewAdapter recyclerViewAdapter;

    public LogFragment() {
        // Required empty public constructor
    }

    public static LogFragment newInstance() {
        return new LogFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() != null) {
            getActivity().setTitle(R.string.logFragment);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs, container, false);
        fragmentView = view;
        recyclerView = view.findViewById(R.id.log_list);
        emptyLogView = view.findViewById(R.id.empty_log_view);

        Context c = view.getContext();
        recyclerView.setLayoutManager(new LinearLayoutManager(c));
        recyclerView.setItemAnimator(new LandingAnimator());

        populateLogs();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        populateLogs();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, 1001, Menu.NONE, "Share Logs")
                .setIcon(R.drawable.ic_export)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, 1002, Menu.NONE, "Clear Logs")
                .setIcon(R.drawable.ic_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Context context = getContext();
        if (context == null) return super.onOptionsItemSelected(item);

        if (item.getItemId() == 1001) {
            // Share / Copy Logs
            ArrayList<JSONObject> logs = SyncLog.getLog(context);
            if (logs.isEmpty()) {
                Toasty.info(context, "No logs to export", Toast.LENGTH_SHORT, true).show();
                return true;
            }
            StringBuilder sb = new StringBuilder("Remote Manager Logs:\n\n");
            for (JSONObject log : logs) {
                sb.append(log.toString()).append("\n");
            }
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Remote Manager Diagnostic Logs");
            shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(shareIntent, "Export Logs"));
            return true;
        } else if (item.getItemId() == 1002) {
            // Clear Logs
            SyncLog.delete(context);
            populateLogs();
            Toasty.success(context, "Logs cleared successfully", Toast.LENGTH_SHORT, true).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void populateLogs() {
        if (getContext() == null || fragmentView == null) return;
        Context c = getContext();

        ArrayList<JSONObject> logs = SyncLog.getLog(c);
        if (logs == null || logs.isEmpty()) {
            if (recyclerView != null) recyclerView.setVisibility(View.GONE);
            if (emptyLogView != null) emptyLogView.setVisibility(View.VISIBLE);
        } else {
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
            if (emptyLogView != null) emptyLogView.setVisibility(View.GONE);
            recyclerViewAdapter = new LogRecyclerViewAdapter(logs);
            if (recyclerView != null) recyclerView.setAdapter(recyclerViewAdapter);
        }
    }
}
