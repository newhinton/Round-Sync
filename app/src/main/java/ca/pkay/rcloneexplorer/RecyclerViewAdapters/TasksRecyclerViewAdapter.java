package ca.pkay.rcloneexplorer.RecyclerViewAdapters;


import static ca.pkay.rcloneexplorer.workmanager.SyncWorker.EXTRA_TASK_ID;
import static ca.pkay.rcloneexplorer.workmanager.SyncWorker.TASK_SYNC_ACTION;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ca.pkay.rcloneexplorer.Activities.ShortcutServiceActivity;
import ca.pkay.rcloneexplorer.Activities.TaskActivity;
import ca.pkay.rcloneexplorer.Database.DatabaseHandler;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.Items.SyncDirectionObject;
import ca.pkay.rcloneexplorer.Items.Task;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.workmanager.SyncManager;
import es.dmoral.toasty.Toasty;

public class TasksRecyclerViewAdapter extends RecyclerView.Adapter<TasksRecyclerViewAdapter.ViewHolder>{

    private static final String clipboardID = "rclone_explorer_task_id";

    private List<Task> tasks;
    private View view;
    private final Context context;


    public TasksRecyclerViewAdapter(List<Task> tasks, Context context) {
        this.tasks = tasks;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_tasks_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final Task selectedTask = tasks.get(position);
        String remoteName = selectedTask.getTitle();

        holder.taskName.setText(remoteName);

        RemoteItem remote = new RemoteItem(selectedTask.getRemoteId(), String.valueOf(selectedTask.getRemoteType()));

        holder.taskIcon.setImageDrawable(AppCompatResources.getDrawable(context, remote.getRemoteIcon(selectedTask.getRemoteType())));

        int direction = selectedTask.getDirection();

        if(direction == SyncDirectionObject.SYNC_LOCAL_TO_REMOTE || direction == SyncDirectionObject.COPY_LOCAL_TO_REMOTE){
            holder.fromID.setVisibility(View.GONE);
            holder.fromPath.setText(selectedTask.getLocalPath());

            holder.toID.setText(String.format("%s:", selectedTask.getRemoteId()));
            holder.toPath.setText(selectedTask.getRemotePath());
        }

        if(direction == SyncDirectionObject.SYNC_REMOTE_TO_LOCAL || direction == SyncDirectionObject.COPY_REMOTE_TO_LOCAL){
            holder.fromID.setText(String.format("%s:", selectedTask.getRemoteId()));
            holder.fromPath.setText(selectedTask.getRemotePath());

            holder.toID.setVisibility(View.GONE);
            holder.toPath.setText(selectedTask.getLocalPath());
        }

        if(direction == SyncDirectionObject.SYNC_BIDIRECTIONAL || direction == SyncDirectionObject.SYNC_BIDIRECTIONAL_INITIAL){
            holder.fromID.setText(String.format("%s:", selectedTask.getRemoteId()));
            holder.fromPath.setText(selectedTask.getRemotePath());

            holder.toID.setVisibility(View.GONE);
            holder.toPath.setText(selectedTask.getLocalPath());

            holder.to.setText(this.context.getString(R.string.task_item_label_to_bisync));
            holder.from.setText(this.context.getString(R.string.task_item_label_from_bisync));
        }

        switch (direction){
            case SyncDirectionObject.COPY_LOCAL_TO_REMOTE:
            case SyncDirectionObject.COPY_REMOTE_TO_LOCAL:
                holder.taskSyncDirection.setText(view.getResources().getString(R.string.copy));
                break;
            case SyncDirectionObject.MOVE_LOCAL_TO_REMOTE:
                holder.taskSyncDirection.setText(view.getResources().getString(R.string.move));
                break;
            case SyncDirectionObject.SYNC_BIDIRECTIONAL_INITIAL:
            case SyncDirectionObject.SYNC_BIDIRECTIONAL:
                holder.taskSyncDirection.setText(view.getResources().getString(R.string.bisync));
                break;
            default:
                holder.taskSyncDirection.setText(view.getResources().getString(R.string.sync));
                break;
        }

        holder.fileOptions.setOnClickListener(v-> {
            showFileMenu(v, selectedTask);
        });

    }

    public void addTask(Task data) {
        tasks.add(data);
        notifyItemInserted(tasks.size());
    }

    public void setList(ArrayList<Task> data) {
        tasks = data;
        notifyDataSetChanged();
    }

    private void startTask(Task task){
        SyncManager sm = new SyncManager(context);
        sm.queue(task);
    }

    private void editTask(Task task) {
        Intent intent = new Intent(context, TaskActivity.class);
        intent.putExtra(TaskActivity.ID_EXTRA, task.getId());
        context.startActivity(intent);
    }

    private void copyTask(Task task) {
        task.setTitle(task.getTitle() + context.getString(R.string.task_copy_suffix));
        Task newTask = (new DatabaseHandler(context)).createTask(task, false);
        tasks.add(newTask);
        notifyItemInserted(tasks.size() - 1);
    }

    public void removeItem(Task task) {
        int index = tasks.indexOf(task);
        if (index >= 0) {
            tasks.remove(index);
            notifyItemRemoved(index);
        }
    }

    @Override
    public int getItemCount() {
        if (tasks == null) {
            return 0;
        }
        return tasks.size();
    }

    private void showFileMenu(View view, final Task task) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.getMenuInflater().inflate(R.menu.task_item_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_start_task:
                    startTask(task);
                    break;
                case R.id.action_edit_task:
                    editTask(task);
                    break;
                case R.id.action_copy_task:
                    copyTask(task);
                    break;
                case R.id.action_delete_task:
                    new DatabaseHandler(context).deleteTask(task.getId());
                    notifyDataSetChanged();
                    removeItem(task);
                    break;
                case R.id.action_copy_id_task:
                    String id = String.valueOf(task.getId());
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(clipboardID, id);
                    clipboard.setPrimaryClip(clip);
                    Toasty.info(context, context.getResources().getString(R.string.task_copied_id_to_clipboard, id), Toast.LENGTH_SHORT, true).show();
                    break;
                case R.id.action_add_to_home_screen:
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        createShortcut(context, task);
                    }
                    break;
                default:
                    return false;
            }
            return true;
        });
        popupMenu.show();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final View view;
        final ImageView taskIcon;
        final TextView taskName;
        final TextView to;
        final TextView from;
        final TextView toID;
        final TextView fromID;
        final TextView toPath;
        final TextView fromPath;
        final ImageButton fileOptions;
        final TextView taskSyncDirection;

        ViewHolder(View itemView) {
            super(itemView);
            this.view = itemView;
            this.taskIcon = view.findViewById(R.id.taskIcon);
            this.taskName = view.findViewById(R.id.taskName);
            this.to = view.findViewById(R.id.toLabel);
            this.from = view.findViewById(R.id.fromLabel);
            this.toID = view.findViewById(R.id.toID);
            this.fromID = view.findViewById(R.id.fromID);
            this.toPath = view.findViewById(R.id.toPath);
            this.fromPath = view.findViewById(R.id.fromPath);
            this.taskSyncDirection = view.findViewById(R.id.task_sync_direction);
            this.fileOptions = view.findViewById(R.id.file_options);
        }
    }

    private static void createShortcut(Context c, Task task) {
        Intent intent = new Intent(TASK_SYNC_ACTION, Uri.EMPTY, c, ShortcutServiceActivity.class);
        intent.setAction(TASK_SYNC_ACTION);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(EXTRA_TASK_ID, task.getId());

        String id = String.valueOf(task.getTitle()+task.getLocalPath()+task.getRemotePath()+task.getDirection());
        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(c, id)
                .setShortLabel(task.getTitle())
                .setLongLabel(task.getRemotePath())
                .setIcon(IconCompat.createWithResource(c, R.drawable.ic_twotone_rounded_cloud_sync_24))
                .setIntent(intent)
                .build();

        ShortcutManagerCompat.requestPinShortcut(c, shortcut, null);

    }

}
