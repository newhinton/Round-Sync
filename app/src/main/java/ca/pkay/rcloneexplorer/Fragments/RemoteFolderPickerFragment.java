package ca.pkay.rcloneexplorer.Fragments;

import static ca.pkay.rcloneexplorer.util.ActivityHelper.tryStartService;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import ca.pkay.rcloneexplorer.Dialogs.GoToDialog;
import ca.pkay.rcloneexplorer.Dialogs.InputDialog;
import ca.pkay.rcloneexplorer.Dialogs.SortDialog;
import ca.pkay.rcloneexplorer.FileComparators;
import ca.pkay.rcloneexplorer.Items.DirectoryObject;
import ca.pkay.rcloneexplorer.Items.FileItem;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.RecyclerViewAdapters.FileExplorerRecyclerViewAdapter;
import ca.pkay.rcloneexplorer.Services.ThumbnailsLoadingService;
import ca.pkay.rcloneexplorer.util.FLog;
import ca.pkay.rcloneexplorer.util.LargeParcel;
import de.felixnuesse.ui.BreadcrumbView;
import es.dmoral.toasty.Toasty;
import jp.wasabeef.recyclerview.animators.LandingAnimator;

public class RemoteFolderPickerFragment extends Fragment implements   FileExplorerRecyclerViewAdapter.OnClickListener,
                                                                            SwipeRefreshLayout.OnRefreshListener,
                                                                            BreadcrumbView.OnClickListener,
                                                                            SortDialog.OnClickListener,
                                                                            InputDialog.OnPositive,
                                                                            GoToDialog.Callbacks {


    private static final String TAG = "FileExplorerFragment";
    private static final String ARG_REMOTE = "remote_param";
    private static final String SHARED_PREFS_SORT_ORDER = "ca.pkay.rcexplorer.sort_order";

    private final String SAVED_PATH = "ca.pkay.rcexplorer.FILE_EXPLORER_FRAG_SAVED_PATH";
    private final String SAVED_CONTENT = "ca.pkay.rcexplorer.FILE_EXPLORER_FRAG_SAVED_CONTENT";
    private final String SAVED_SEARCH_MODE = "ca.pkay.rcexplorer.FILE_EXPLORER_FRAG_SEARCH_MODE";
    private final String SAVED_SEARCH_STRING = "ca.pkay.rcexplorer.FILE_EXPLORER_FRAG_SEARCH_STRING";
    private final String SAVED_RENAME_ITEM = "ca.pkay.rcexplorer.FILE_EXPLORER_FRAG_RENAME_ITEM";
    private final String SAVED_SELECTED_ITEMS = "ca.pkay.rcexplorer.FILE_EXPLORER_FRAG_SELECTED_ITEMS";
    private final String SAVED_START_AT_BOOT = "ca.pkay.rcexplorer.FILE_EXPLORER_FRAG_START_AT_BOOT";


    private Stack<String> pathStack;
    private Map<String, Integer> directoryPosition;
    private DirectoryObject directoryObject;

    private BreadcrumbView breadcrumbView;
    private Rclone rclone;
    private RemoteItem remote;
    private String remoteName;
    private FileExplorerRecyclerViewAdapter recyclerViewAdapter;
    private LinearLayoutManager recyclerViewLinearLayoutManager;
    private SwipeRefreshLayout swipeRefreshLayout;

    private AsyncTask fetchDirectoryTask;

    private String originalToolbarTitle;
    private int sortOrder;
    private FloatingActionButton fab;
    private Boolean isSearchMode;
    private String searchString;
    private boolean showThumbnails;
    private boolean isThumbnailsServiceRunning;
    private boolean startAtRoot;
    private boolean goToDefaultSet;
    private Context context;
    private String thumbnailServerAuth;
    private int thumbnailServerPort;
    private boolean wrapFilenames;
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;

    private FolderSelectorCallback mSelectedFolderCallback;
    private String mInitialPath = "";

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public RemoteFolderPickerFragment() {}

    @SuppressWarnings("unused")
    public static RemoteFolderPickerFragment newInstance(RemoteItem remoteItem, FolderSelectorCallback fsc, String initialPath) {
        RemoteFolderPickerFragment fragment = new RemoteFolderPickerFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_REMOTE, remoteItem);
        fragment.setArguments(args);
        fragment.setSelectedFolderCallback(fsc);
        fragment.setInitialPath(initialPath);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() == null) {
            return;
        }

        if (getContext() == null) {
            return;
        }
        setHasOptionsMenu(true);

        remote = getArguments().getParcelable(ARG_REMOTE);
        if (remote == null) {
            return;
        }
        remoteName = remote.getName();
        pathStack = new Stack<>();
        directoryPosition = new HashMap<>();
        directoryObject = new DirectoryObject();

        String path;
        if (savedInstanceState == null) {
            path = "//" + remoteName;
            directoryObject.setPath(path);
        } else {
            path = savedInstanceState.getString(SAVED_PATH);
            if (path == null) {
                return;
            }
            directoryObject.setPath(path);
            ArrayList<FileItem> savedContent = savedInstanceState.getParcelableArrayList(SAVED_CONTENT);
            if(null == savedContent){
                directoryObject.clear();
            } else {
                directoryObject.setContent(savedContent);
            }

            buildStackFromPath(remoteName, path);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sortOrder = sharedPreferences.getInt(SHARED_PREFS_SORT_ORDER, SortDialog.ALPHA_ASCENDING);
        showThumbnails = sharedPreferences.getBoolean(getString(R.string.pref_key_show_thumbnails), false);
        goToDefaultSet = sharedPreferences.getBoolean(getString(R.string.pref_key_go_to_default_set), false);
        String wrapFilenamesKey = getString(R.string.pref_key_wrap_filenames);
        prefChangeListener = (pref, key) -> {
            if (key.equals(wrapFilenamesKey) && recyclerViewAdapter != null) {
                recyclerViewAdapter.setWrapFileNames(pref.getBoolean(wrapFilenamesKey, true));
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener);
        wrapFilenames = sharedPreferences.getBoolean(getString(R.string.pref_key_wrap_filenames), true);

        if (goToDefaultSet) {
            startAtRoot = sharedPreferences.getBoolean(getString(R.string.pref_key_start_at_root), false);
        }

        rclone = new Rclone(getContext());

        isSearchMode = false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_remote_folder_picker_list, container, false);
        if (savedInstanceState != null) {
            startAtRoot = savedInstanceState.getBoolean(SAVED_START_AT_BOOT);
        }

        if (showThumbnails) {
            initializeThumbnailParams();
            startThumbnailService();
        }

        swipeRefreshLayout = view.findViewById(R.id.file_explorer_srl);
        swipeRefreshLayout.setOnRefreshListener(this);

        Context context = view.getContext();

        RecyclerView recyclerView = view.findViewById(R.id.file_explorer_list);
        recyclerViewLinearLayoutManager = new LinearLayoutManager(context);
        recyclerView.setItemAnimator(new LandingAnimator());
        recyclerView.setLayoutManager(recyclerViewLinearLayoutManager);
        View emptyFolderView = view.findViewById(R.id.empty_folder_view);
        View noSearchResultsView = view.findViewById(R.id.no_search_results_view);
        recyclerViewAdapter = new FileExplorerRecyclerViewAdapter(context, emptyFolderView, noSearchResultsView, this);
        recyclerViewAdapter.showThumbnails(showThumbnails);
        recyclerViewAdapter.setWrapFileNames(wrapFilenames);
        recyclerViewAdapter.disableFileOptions();
        recyclerView.setAdapter(recyclerViewAdapter);

        if (remote.isRemoteType(RemoteItem.SFTP) && !goToDefaultSet & savedInstanceState == null) {
            showSFTPgoToDialog();
        } else {
            if (directoryObject.isDirectoryContentEmpty()) {
                fetchDirectoryTask = new FetchDirectoryContent().execute();
                swipeRefreshLayout.setRefreshing(true);
            } else {
                recyclerViewAdapter.newData(directoryObject.getDirectoryContent());
            }
        }

        fab = view.findViewById(R.id.selectFloatingButton);


        originalToolbarTitle = ((FragmentActivity) context).getTitle().toString();
        setTitle();
        breadcrumbView = ((FragmentActivity) context).findViewById(R.id.breadcrumb_view);
        breadcrumbView.setOnClickListener(this);
        breadcrumbView.setVisibility(View.VISIBLE);
        // this will be called twice for an unknown reason. Therefore we need to clear the Crumbs.
        breadcrumbView.clearCrumbs();
        if (!mInitialPath.isEmpty()) {
            directoryObject.setPath(mInitialPath);
            breadcrumbView.buildBreadCrumbsFromPath(remote.getDisplayName()+directoryObject.getCurrentPath());
        } else {
            breadcrumbView.addCrumb(remote.getDisplayName(), "//");
        }

        if (savedInstanceState != null && savedInstanceState.getBoolean(SAVED_SEARCH_MODE, false)) {
            searchString = savedInstanceState.getString(SAVED_SEARCH_STRING);
            searchClicked();
        }

        if(mSelectedFolderCallback != null) {
            fab.setOnClickListener(v -> {
                String target = "/"+directoryObject.getCurrentPath();
                if(target.startsWith("///")){
                    target = target.replace("//", "");
                }
                mSelectedFolderCallback.selectFolder(target);
                exitFragment();
            });
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        registerReceivers();

        if (showThumbnails) {
            startThumbnailService();
        }

        if (directoryObject.isContentValid()) {
            return;
        }
        if (fetchDirectoryTask != null) {
            fetchDirectoryTask.cancel(true);
        }
        swipeRefreshLayout.setRefreshing(true);
        fetchDirectoryTask = new FetchDirectoryContent(true).execute();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVED_PATH, directoryObject.getCurrentPath());
        ArrayList<FileItem> content = new ArrayList<>(directoryObject.getDirectoryContent());
        outState.putParcelableArrayList(SAVED_CONTENT, content);
        outState.putBoolean(SAVED_START_AT_BOOT, startAtRoot);
        if (isSearchMode) {
            outState.putString(SAVED_SEARCH_STRING, searchString);
        }
        if (recyclerViewAdapter.isInSelectMode()) {
            outState.putParcelableArrayList(SAVED_SELECTED_ITEMS, new ArrayList<>(recyclerViewAdapter.getSelectedItems()));
        }

        if (LargeParcel.calculateBundleSize(outState) > 250 * 1024) {
            outState.remove(SAVED_CONTENT);
        }
    }



    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
    }

    private void setTitle() {
        ((FragmentActivity) context).setTitle(getString(R.string.remote_folder_picker_title));
    }

    private void buildStackFromPath(String remote, String path) {
        String root = "//" + remote;
        if (root.equals(path)) {
            return;
        }
        pathStack.clear();
        pathStack.push(root);

        int index = 0;

        while ((index = path.indexOf("/", index)) > 0) {
            String p = path.substring(0, index);
            pathStack.push(p);
            index++;
        }
    }

    private void registerReceivers() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(getString(R.string.background_service_broadcast));
        LocalBroadcastManager.getInstance(context).registerReceiver(backgroundTaskBroadcastReceiver, intentFilter);
    }

    private BroadcastReceiver backgroundTaskBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String broadcastRemote = intent.getStringExtra(getString(R.string.background_service_broadcast_data_remote));
            String broadcastPath = intent.getStringExtra(getString(R.string.background_service_broadcast_data_path));
            String broadcastPath2 = intent.getStringExtra(getString(R.string.background_service_broadcast_data_path2));
            String path = directoryObject.getCurrentPath();
            if (!remoteName.equals(broadcastRemote)) {
                return;
            }

            if (path.equals(broadcastPath)) {
                if (fetchDirectoryTask != null) {
                    fetchDirectoryTask.cancel(true);
                }
                if (directoryObject.isPathInCache(broadcastPath)) {
                    directoryObject.removePathFromCache(broadcastPath);
                }
                fetchDirectoryTask = new FetchDirectoryContent(true).execute();
            } else if (directoryObject.isPathInCache(broadcastPath)) {
                directoryObject.removePathFromCache(broadcastPath);
            }

            if (broadcastPath2 == null) {
                return;
            }

            if (path.equals(broadcastPath2)) {
                if (fetchDirectoryTask != null) {
                    fetchDirectoryTask.cancel(true);
                }
                swipeRefreshLayout.setRefreshing(false);
                if (directoryObject.isPathInCache(broadcastPath2)) {
                    directoryObject.removePathFromCache(broadcastPath2);
                }
                fetchDirectoryTask = new FetchDirectoryContent(true).execute();
            } else if (directoryObject.isPathInCache(broadcastPath2)) {
                directoryObject.removePathFromCache(broadcastPath2);
            }
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.remote_folder_picker_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                searchClicked();
                return true;
            case R.id.action_sort:
                showSortMenu();
                return true;
            case R.id.action_go_to:
                showSFTPgoToDialog();
                return true;
            case R.id.action_new_folder:
                onCreateNewDirectory();
                return true;
            case android.R.id.home:
                exitFragment();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void exitFragment() {
        breadcrumbView.clearCrumbs();
        ((FragmentActivity) context).setTitle(originalToolbarTitle);
        FragmentManager fm = this.getActivity().getSupportFragmentManager();
        fm.beginTransaction().remove(this).commit();
    }

    private void showSFTPgoToDialog() {
        GoToDialog goToDialog = new GoToDialog();
        goToDialog.show(getChildFragmentManager(), "go to dialog");
    }

    /*
     * Swipe to refresh
     */
    @Override
    public void onRefresh() {
        if (fetchDirectoryTask != null) {
            fetchDirectoryTask.cancel(true);
        }
        fetchDirectoryTask = new FetchDirectoryContent(true).execute();
    }

    private void startThumbnailService() {
        if(RemoteItem.SAFW == remote.getType()){
            // safdav also serves files for thumbnails
            return;
        }
        Intent serveIntent = new Intent(getContext(), ThumbnailsLoadingService.class);
        serveIntent.putExtra(ThumbnailsLoadingService.REMOTE_ARG, remote);
        serveIntent.putExtra(ThumbnailsLoadingService.HIDDEN_PATH, thumbnailServerAuth);
        serveIntent.putExtra(ThumbnailsLoadingService.SERVER_PORT, thumbnailServerPort);
        tryStartService(context, serveIntent);
        isThumbnailsServiceRunning = true;
    }

    private void initializeThumbnailParams() {
        SecureRandom random = new SecureRandom();
        byte[] values = new byte[16];
        random.nextBytes(values);
        thumbnailServerAuth = Base64.encodeToString(values, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
        thumbnailServerPort = allocatePort(29179, true);
    }

    private static int allocatePort(int port, boolean allocateFallback) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            if (allocateFallback) {
                return allocatePort(0, false);
            }
        }
        throw new IllegalStateException("No port available");
    }

    private void searchClicked() {
        if (isSearchMode) {
            //breadcrumbView.setVisibility(View.VISIBLE);
            searchDirContent("");
            recyclerViewAdapter.setSearchMode(false);
            isSearchMode = false;
        } else {
            //breadcrumbView.setVisibility(View.GONE);
            recyclerViewAdapter.setSearchMode(true);
            isSearchMode = true;
        }
    }


    private void searchDirContent(String search) {
        List<FileItem> content = directoryObject.getDirectoryContent();
        List<FileItem> currentShown = recyclerViewAdapter.getCurrentContent();
        List<FileItem> results = new ArrayList<>();

        searchString = search;

        if (search.isEmpty()) {
            if (currentShown.equals(content)) {
                return;
            } else {
                recyclerViewAdapter.newData(content);
            }
        }

        for (FileItem item : content) {
            String fileName = item.getName().toLowerCase(Locale.ROOT);
            if (fileName.contains(search.toLowerCase(Locale.ROOT))) {
                results.add(item);
            }
        }

        if (currentShown.equals(results)) {
            return;
        }
        recyclerViewAdapter.newData(results);
    }

    private void showSortMenu() {
        SortDialog sortDialog = new SortDialog();
        sortDialog
                .setTitle(R.string.sort)
                .setNegativeButton(R.string.cancel)
                .setPositiveButton(R.string.ok)
                .setSortOrder(sortOrder);
        sortDialog.show(getChildFragmentManager(), "sort dialog");
    }


    private void onCreateNewDirectory() {
        new InputDialog()
                .setTitle(R.string.create_new_folder)
                .setMessage(R.string.type_new_folder_name)
                .setNegativeButton(R.string.cancel)
                .setPositiveButton(R.string.okay_confirmation)
                .setHint(R.string.hint_new_folder)
                .show(getChildFragmentManager(), "input dialog");
    }

    /*
     * Input Dialog callback
     */
    @Override
    public void onPositive(String tag, String input) {
        if (input.trim().length() == 0) {
            return;
        }
        String newDir;
        if (directoryObject.getCurrentPath().equals("//" + remote.getName())) {
            newDir = input;
        } else {
            newDir = directoryObject.getCurrentPath() + "/" + input;
        }
        rclone.makeDirectory(remote, newDir);
    }


    /*
     * Sort Dialog callback
     */
    @Override
    public void onPositiveButtonClick(int sortById, int sortOrderId) {
        if (!directoryObject.isDirectoryContentEmpty()) {
            sortSelected(sortById, sortOrderId);
        }
    }

    private void sortSelected(int sortById, int sortOrderId) {
        List<FileItem> directoryContent = directoryObject.getDirectoryContent();

        switch (sortById) {
            case R.id.radio_sort_name:
                if (sortOrderId == R.id.radio_sort_ascending) {
                    Collections.sort(directoryContent, new FileComparators.SortAlphaAscending());
                    sortOrder = SortDialog.ALPHA_ASCENDING;
                } else {
                    Collections.sort(directoryContent, new FileComparators.SortAlphaDescending());
                    sortOrder = SortDialog.ALPHA_DESCENDING;
                }
                break;
            case R.id.radio_sort_date:
                if (sortOrderId == R.id.radio_sort_ascending) {
                    Collections.sort(directoryContent, new FileComparators.SortModTimeAscending());
                    sortOrder = SortDialog.MOD_TIME_ASCENDING;
                } else {
                    Collections.sort(directoryContent, new FileComparators.SortModTimeDescending());
                    sortOrder = SortDialog.MOD_TIME_DESCENDING;
                }
                break;
            case R.id.radio_sort_size:
                if (sortOrderId == R.id.radio_sort_ascending) {
                    Collections.sort(directoryContent, new FileComparators.SortSizeAscending());
                    sortOrder = SortDialog.SIZE_ASCENDING;
                } else {
                    Collections.sort(directoryContent, new FileComparators.SortSizeDescending());
                    sortOrder = SortDialog.SIZE_DESCENDING;
                }
                break;
        }
        directoryObject.setContent(directoryContent);

        if (isSearchMode) {
            List<FileItem> sortedSearch = new ArrayList<>();
            List<FileItem> searchResult = recyclerViewAdapter.getCurrentContent();
            for (FileItem item : directoryContent) {
                if (searchResult.contains(item)) {
                    sortedSearch.add(item);
                }
            }
            recyclerViewAdapter.updateSortedData(sortedSearch);
        } else {
            recyclerViewAdapter.updateSortedData(directoryContent);
        }
        if (sortOrder > 0) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            sharedPreferences.edit().putInt(SHARED_PREFS_SORT_ORDER, sortOrder).apply();
        }
    }

    private void sortDirectory() {
        List<FileItem> directoryContent = directoryObject.getDirectoryContent();
        switch (sortOrder) {
            case SortDialog.MOD_TIME_DESCENDING:
                Collections.sort(directoryContent, new FileComparators.SortModTimeDescending());
                sortOrder = SortDialog.MOD_TIME_ASCENDING;
                break;
            case SortDialog.MOD_TIME_ASCENDING:
                Collections.sort(directoryContent, new FileComparators.SortModTimeAscending());
                sortOrder = SortDialog.MOD_TIME_DESCENDING;
                break;
            case SortDialog.SIZE_DESCENDING:
                Collections.sort(directoryContent, new FileComparators.SortSizeDescending());
                sortOrder = SortDialog.SIZE_ASCENDING;
                break;
            case SortDialog.SIZE_ASCENDING:
                Collections.sort(directoryContent, new FileComparators.SortSizeAscending());
                sortOrder = SortDialog.SIZE_DESCENDING;
                break;
            case SortDialog.ALPHA_ASCENDING:
                Collections.sort(directoryContent, new FileComparators.SortAlphaAscending());
                sortOrder = SortDialog.ALPHA_ASCENDING;
                break;
            case SortDialog.ALPHA_DESCENDING:
            default:
                Collections.sort(directoryContent, new FileComparators.SortAlphaDescending());
                sortOrder = SortDialog.ALPHA_DESCENDING;
        }
        directoryObject.setContent(directoryContent);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isThumbnailsServiceRunning) {
            Intent intent = new Intent(context, ThumbnailsLoadingService.class);
            context.stopService(intent);
            isThumbnailsServiceRunning = false;
        }
        ((FragmentActivity) context).setTitle(originalToolbarTitle);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(backgroundTaskBroadcastReceiver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (fetchDirectoryTask != null) {
            fetchDirectoryTask.cancel(true);
        }
        ((FragmentActivity) context).setTitle(originalToolbarTitle);
        breadcrumbView.clearCrumbs();
        breadcrumbView.setVisibility(View.GONE);
        prefChangeListener = null;
        context = null;
    }

    @Override
    public void onFileClicked(FileItem fileItem) {}

    @Override
    public void onDirectoryClicked(FileItem fileItem, int position) {
        directoryPosition.put(directoryObject.getCurrentPath(), position);
        breadcrumbView.addCrumb(fileItem.getName(), fileItem.getPath());
        swipeRefreshLayout.setRefreshing(true);
        pathStack.push(directoryObject.getCurrentPath());

        if (isSearchMode) {
            searchClicked();
        }

        if (fetchDirectoryTask != null) {
            fetchDirectoryTask.cancel(true);
        }

        if (!directoryObject.isContentValid(fileItem.getPath())) {
            swipeRefreshLayout.setRefreshing(true);
            directoryObject.restoreFromCache(fileItem.getPath());
            sortDirectory();
            recyclerViewAdapter.newData(directoryObject.getDirectoryContent());
            fetchDirectoryTask = new FetchDirectoryContent(true).execute();
        } else if (directoryObject.isPathInCache(fileItem.getPath())) {
            directoryObject.restoreFromCache(fileItem.getPath());
            sortDirectory();
            recyclerViewAdapter.newData(directoryObject.getDirectoryContent());
            swipeRefreshLayout.setRefreshing(false);
        } else {
            directoryObject.setPath(fileItem.getPath());
            recyclerViewAdapter.clear();
            fetchDirectoryTask = new FetchDirectoryContent().execute();
        }
    }

    @Override
    public void onFilesSelected() {}
    @Override
    public void onFileDeselected() {}
    @Override
    public void onFileOptionsClicked(View view, FileItem fileItem) {}

    @Override
    public String[] getThumbnailServerParams() {
        return new String[]{thumbnailServerAuth + '/' + remote.getName(), String.valueOf(thumbnailServerPort)};
    }

    @Override
    public void onBreadCrumbClicked(String path) {
        if (isSearchMode) {
            searchClicked();
        }
        if (directoryObject.getCurrentPath().equals(path)) {
            return;
        }
        swipeRefreshLayout.setRefreshing(false);
        if (fetchDirectoryTask != null) {
            fetchDirectoryTask.cancel(true);
        }
        directoryObject.setPath(path);
        //noinspection StatementWithEmptyBody
        while (!pathStack.empty() && !pathStack.pop().equals(path)) {
            // pop stack until we find path
        }
        breadcrumbView.removeCrumbsUpTo(path);
        recyclerViewAdapter.clear();

        if (!directoryObject.isContentValid(path)) {
            swipeRefreshLayout.setRefreshing(true);
            directoryObject.restoreFromCache(path);
            sortDirectory();
            recyclerViewAdapter.newData(directoryObject.getDirectoryContent());
            if (directoryPosition.containsKey(directoryObject.getCurrentPath())) {
                int position = directoryPosition.get(directoryObject.getCurrentPath());
                recyclerViewLinearLayoutManager.scrollToPositionWithOffset(position, 10);
            }
            fetchDirectoryTask = new FetchDirectoryContent(true).execute();
        } else if (directoryObject.isPathInCache(path)) {
            directoryObject.restoreFromCache(path);
            sortDirectory();
            recyclerViewAdapter.newData(directoryObject.getDirectoryContent());
            if (directoryPosition.containsKey(directoryObject.getCurrentPath())) {
                int position = directoryPosition.get(directoryObject.getCurrentPath());
                recyclerViewLinearLayoutManager.scrollToPositionWithOffset(position, 10);
            }
        } else {
            fetchDirectoryTask = new FetchDirectoryContent().execute();
        }
    }


    /*
     * Go To Dialog Callback
     */
    @Override
    public void onRootClicked(boolean isSetAsDefault) {
        startAtRoot = true;
        directoryObject.clear();
        String path = "//" + remoteName;
        directoryObject.setPath(path);
        swipeRefreshLayout.setRefreshing(true);
        fetchDirectoryTask = new FetchDirectoryContent().execute();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (isSetAsDefault) {
            editor.putBoolean(getString(R.string.pref_key_go_to_default_set), true);
            editor.putBoolean(getString(R.string.pref_key_start_at_root), true);
        } else {
            editor.putBoolean(getString(R.string.pref_key_go_to_default_set), false);
        }
        editor.apply();
    }

    /*
     * Go To Dialog Callback
     */
    @Override
    public void onHomeClicked(boolean isSetAsDefault) {
        startAtRoot = false;
        directoryObject.clear();
        String path = "//" + remoteName;
        directoryObject.setPath(path);
        swipeRefreshLayout.setRefreshing(true);
        fetchDirectoryTask = new FetchDirectoryContent().execute();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (isSetAsDefault) {
            editor.putBoolean(getString(R.string.pref_key_go_to_default_set), true);
            editor.putBoolean(getString(R.string.pref_key_start_at_root), false);
        } else {
            editor.putBoolean(getString(R.string.pref_key_go_to_default_set), false);
        }
        editor.apply();
    }

    public void setInitialPath(String initialPath) {
        mInitialPath = initialPath;
    }
    public void setSelectedFolderCallback(FolderSelectorCallback fsc) {
        mSelectedFolderCallback = fsc;
    }

    /***********************************************************************************************
     * AsyncTask classes
     ***********************************************************************************************/
    @SuppressLint("StaticFieldLeak")
    private class FetchDirectoryContent extends AsyncTask<Void, Void, List<FileItem>> {

        private boolean silentFetch;

        FetchDirectoryContent() {
            this(false);
        }

        FetchDirectoryContent(boolean silentFetch) {
            this.silentFetch = silentFetch;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected List<FileItem> doInBackground(Void... voids) {
            List<FileItem> fileItemList;
            fileItemList = rclone.getDirectoryContent(remote, directoryObject.getCurrentPath(), startAtRoot);
            return fileItemList;
        }

        @Override
        protected void onPostExecute(List<FileItem> fileItems) {
            super.onPostExecute(fileItems);
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            if (null == getContext()) {
                FLog.w(TAG, "FetchDirectoryContent/onPostExecute: discarding refresh");
                return;
            }
            if (fileItems == null) {
                if (silentFetch) {
                    return;
                }
                FLog.w(TAG, "FetchDirectoryContent/onPostExecute: "+getString(R.string.error_getting_dir_content));
                Toasty.error(context, getString(R.string.error_getting_dir_content), Toast.LENGTH_SHORT, true).show();
                fileItems = new ArrayList<>();
            }

            directoryObject.setContent(fileItems);
            sortDirectory();

            if (isSearchMode && searchString != null) {
                searchDirContent(searchString);
            } else {
                if (recyclerViewAdapter != null) {
                    if (silentFetch) {
                        recyclerViewAdapter.updateData(directoryObject.getDirectoryContent());
                    } else {
                        recyclerViewAdapter.newData(directoryObject.getDirectoryContent());
                    }
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }
}
