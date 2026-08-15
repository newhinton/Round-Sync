package ca.pkay.rcloneexplorer.RecyclerViewAdapters;

import android.view.View;
import ca.pkay.rcloneexplorer.Items.FileItem;

public interface FileExplorerClickListener {
    void onFileClicked(FileItem fileItem);
    void onDirectoryClicked(FileItem fileItem, int position);
    void onFilesSelected();
    void onFileDeselected();
    void onFileOptionsClicked(View view, FileItem fileItem);
    String[] getThumbnailServerParams();
}
