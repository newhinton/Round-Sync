package ca.pkay.rcloneexplorer.RecyclerViewAdapters;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import ca.pkay.rcloneexplorer.Items.FileItem;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Locale;

import ca.pkay.rcloneexplorer.R;

public class FilePickerAdapter extends RecyclerView.Adapter<FilePickerAdapter.ViewHolder> {

    public interface OnClickListener {
        void onDirectoryClicked(File file);
        void onFileClicked(File file);
        void onSelectionChanged(boolean isSelected);
    }

    private Context context;
    private View emptyView;
    private ArrayList<File> fileList;
    private ArrayList<File> selectedFiles;
    private OnClickListener listener;
    private boolean destinationPickerType;

    public FilePickerAdapter(Context context, ArrayList<File> fileList, boolean destinationPicker, View emptyView) {
        this.context = context;
        this.emptyView = emptyView;
        this.fileList = new ArrayList<>(fileList);
        this.listener = (OnClickListener) context;
        this.destinationPickerType = destinationPicker;
        selectedFiles = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_picker_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final File file = fileList.get(position);

        if (file.isDirectory()) {
            holder.fileIcon.setVisibility(View.INVISIBLE);
            holder.dirIcon.setVisibility(View.VISIBLE);

            holder.interpunct.setVisibility(View.INVISIBLE);
            holder.fileSize.setVisibility(View.INVISIBLE);
        } else {
            holder.fileIcon.setVisibility(View.VISIBLE);
            holder.dirIcon.setVisibility(View.INVISIBLE);

            holder.interpunct.setVisibility(View.VISIBLE);
            holder.fileSize.setVisibility(View.VISIBLE);

            holder.fileSize.setText(sizeToHumanReadable(file.length()));

            String mimeType = FileItem.getMimeType("application/octet-stream", file.getPath());
            if (mimeType != null && mimeType.startsWith("image")) {
                holder.fileIcon.setImageTintList(null);
                coil.Coil.imageLoader(context).enqueue(
                    new coil.request.ImageRequest.Builder(context)
                        .data(file)
                        .target(holder.fileIcon)
                        .placeholder(R.drawable.ic_file)
                        .error(R.drawable.ic_file)
                        .build()
                );
            } else {
                holder.fileIcon.setImageTintList(null);
                holder.fileIcon.setImageResource(R.drawable.ic_file);
            }
        }

        long now = System.currentTimeMillis();
        CharSequence humanReadable = DateUtils.getRelativeTimeSpanString(file.lastModified(), now, DateUtils.MINUTE_IN_MILLIS);
        holder.fileModTime.setText(humanReadable.toString());
        holder.fileName.setText(file.getName());

        if (destinationPickerType) {
            holder.checkBox.setVisibility(View.GONE);

            if (file.isDirectory()) {
                holder.view.setAlpha(1f);
            } else {
                holder.view.setAlpha(.5f);
            }
        } else {
            if (holder.view.getAlpha() == .5f) {
                holder.view.setAlpha(1f);
            }

            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(selectedFiles.contains(file));

            holder.checkBox.setOnClickListener(v -> toggleSelection(file));

            holder.view.setOnLongClickListener(v -> {
                toggleSelection(file);
                return true;
            });
        }
        holder.view.setOnClickListener(v -> {
            if (file.isDirectory()) {
                listener.onDirectoryClicked(file);
            } else if (!destinationPickerType) {
                toggleSelection(file);
           }
        });
    }

    private void toggleSelection(File file) {
        //listener.onFileClicked(file);
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        notifyItemChanged(fileList.indexOf(file));

        if (selectedFiles.isEmpty()) {
            listener.onSelectionChanged(false);
        } else {
            listener.onSelectionChanged(true);
        }
    }

    public void setNewData(ArrayList<File> data) {
        if (data.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }

        fileList.clear();
        fileList = new ArrayList<>(data);
        selectedFiles.clear();
        notifyDataSetChanged();
    }

    public void updateData(ArrayList<File> data) {
        fileList.clear();
        fileList = new ArrayList<>(data);
        notifyDataSetChanged();
    }

    public void toggleSelectAll() {
        if (selectedFiles.isEmpty()) {
            selectedFiles.addAll(fileList);
        } else {
            selectedFiles.clear();
        }
        notifyDataSetChanged();

        if (selectedFiles.isEmpty()) {
            listener.onSelectionChanged(false);
        } else {
            listener.onSelectionChanged(true);
        }
    }

    public void setSelectedFiles(ArrayList<File> data) {
        selectedFiles = new ArrayList<>(data);
        notifyDataSetChanged();

        if (selectedFiles.isEmpty()) {
            listener.onSelectionChanged(false);
        } else {
            listener.onSelectionChanged(true);
        }
    }

    public ArrayList<File> getSelectedFiles() {
        return selectedFiles;
    }

    public boolean isDataSelected() {
        return !selectedFiles.isEmpty();
    }

    @Override
    public int getItemCount() {
        if (fileList == null) {
            return 0;
        }
        return fileList.size();
    }

    private String sizeToHumanReadable(long size) {
        int unit = 1000;
        if (size < unit) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(unit));
        Character pre = "kMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.1f %sB", size / Math.pow(unit, exp), pre);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        public final View view;
        public final ImageView fileIcon;
        public final ImageView dirIcon;
        public final TextView fileName;
        public final TextView fileModTime;
        public final TextView fileSize;
        public final TextView interpunct;
        public final CheckBox checkBox;

        public ViewHolder(View itemView) {
            super(itemView);
            view = itemView;
            fileIcon = itemView.findViewById(R.id.file_icon);
            dirIcon = itemView.findViewById(R.id.folder_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileModTime = itemView.findViewById(R.id.file_modtime);
            fileSize = itemView.findViewById(R.id.file_size);
            interpunct = itemView.findViewById(R.id.interpunct);
            checkBox = itemView.findViewById(R.id.file_checkbox);
        }
    }
}
