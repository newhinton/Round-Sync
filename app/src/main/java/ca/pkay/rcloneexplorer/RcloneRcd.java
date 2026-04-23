package ca.pkay.rcloneexplorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Base64;
import android.util.SparseArray;

import androidx.annotation.IntDef;
import androidx.preference.PreferenceManager;
import ca.pkay.rcloneexplorer.util.FLog;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ca.pkay.rcloneexplorer.util.Rfc3339Deserializer;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The NG version of rclone command runner using rc/rcd. The backbone for
 * VirtualContentProvider.
 */
public class RcloneRcd {

    private static final String TAG = "RcloneRcd";
    private static final MediaType JSON = MediaType.parse("application/json");

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({RUNNING, EXITED, ERROR})
    public @interface ProcessState {
    }

    private static final int RUNNING = 0;
    private static final int EXITED = -1;
    private static final int ERROR = 1;

    private static String rcUser = "admin";
    private static String rcPass = initPass();

    private final Context context;
    //private final Log2File log2File;
    private final ObjectMapper mapper;

    private final String configPath;
    private final String rclone;
    private Process rcd;
    private boolean stopped = false;
    private Object mainThreadLock = new Object();

    final BlockingQueue<Integer> pendingJobs;
    final SparseArray<JobStatusHandler> jobsHandlers;
    final SparseArray<JobStatusResponse> lastStatus;
    private final ScheduledExecutorService jobMonitorService;
    final JobsUpdateHandler jobsUpdateHandler;

    private ScheduledFuture<?> jobsUpdateFuture;
    private int port;
    private Handler mainHandler;

    private static String initPass() {
        SecureRandom random = new SecureRandom();
        byte[] values = new byte[16];
        random.nextBytes(values);
        return Base64.encodeToString(values, Base64.NO_WRAP | Base64.URL_SAFE);
    }

    public RcloneRcd(Context context, JobsUpdateHandler handler) {
        this.context = context;
        this.jobsUpdateHandler = handler;
        configPath = context.getFilesDir().getPath() + "/rclone.conf";
        rclone = context.getApplicationInfo().nativeLibraryDir + "/librclone.so";
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NON_PRIVATE);
        pendingJobs = new LinkedBlockingQueue<>();
        jobsHandlers = new SparseArray<>();
        lastStatus = new SparseArray<>();
        jobMonitorService = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Starts the rclone daemon subprocess and a job monitor thread
     */
    public void startRcd() {
        try {
            FLog.d(TAG, "startRcd: starting rclone process");
            port = nextAvailablePort();
            String addr = "localhost:" + port;
            String tmpDir = context.getCacheDir().getAbsolutePath();
            String logFile = context.getExternalFilesDir("logs").getAbsolutePath() + "/rcd.log";
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            ArrayList<String> parameters = new ArrayList<>(Arrays.asList(
                    rclone,
                    "--config", configPath,
                    "--rc-addr", addr,
                    "--rc-user", rcUser,
                    "--rc-pass", rcPass,
                    "--rc-serve"));
            if (pref.getBoolean(context.getString(R.string.pref_key_logs), false)) {
                parameters.addAll(Arrays.asList(
                        "--log-file", logFile,
                        "--dump", "headers",
                        "-vvv"));
            }
            parameters.add("rcd");
            rcd = Runtime.getRuntime().exec(parameters.toArray(new String[0]), getEnv());
        } catch (IOException e) {
            FLog.e(TAG, "startRcd: error", e);
            throw new RuntimeException(e);
        }
        // PendingRcloneJobs blocks thread until first job arrives
        jobsUpdateFuture = jobMonitorService.scheduleWithFixedDelay(new PendingRcloneJobs(), 0, 1, TimeUnit.SECONDS);
    }

    public String[] getEnv() {
        ArrayList<String> environmentValues = new ArrayList<>();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

        boolean proxyEnabled = pref.getBoolean(context.getString(R.string.pref_key_use_proxy), false);
        if(proxyEnabled) {
            String noProxy = pref.getString(context.getString(R.string.pref_key_no_proxy_hosts), "localhost");
            String protocol = pref.getString(context.getString(R.string.pref_key_proxy_protocol), "http");
            String host = pref.getString(context.getString(R.string.pref_key_proxy_host), "localhost");
            String user = pref.getString(context.getString(R.string.pref_key_proxy_username), "user");
            String pass = pref.getString(context.getString(R.string.pref_key_proxy_password), "pass");
            int port = pref.getInt(context.getString(R.string.pref_key_proxy_port), 8080);
            String auth = "";
            if(!(user + pass).isEmpty()) {
                auth = user+":"+pass+"@";
            }
            String url = protocol + "://" + auth + host + ":" + port;
            // per https://golang.org/pkg/net/http/#ProxyFromEnvironment
            environmentValues.add("http_proxy=" + url);
            environmentValues.add("https_proxy=" + url);
            environmentValues.add("no_proxy=" + noProxy);
        }

        // if TMPDIR is not set, golang uses /data/local/tmp which is only
        // only accessible for the shell user
        String tmpDir = context.getCacheDir().getAbsolutePath();
        environmentValues.add("TMPDIR=" + tmpDir);

        // ignore chtimes errors
        // ref: https://github.com/rclone/rclone/issues/2446
        environmentValues.add("RCLONE_LOCAL_NO_SET_MODTIME=true");

        // The pre-built linux rclone binaries do not know how to find the Android certificate store.
        // We set SSL_CERT_DIR to Android's native certificate store path.
        environmentValues.add("SSL_CERT_DIR=/system/etc/security/cacerts");

        return environmentValues.toArray(new String[0]);
    }

    /**
     * Warning: do not hand out to clients, this contains rc auth data!
     * @return
     */
    public String getServeBase() {
        return new StringBuilder()
                .append("http://")
                .append(rcUser)
                .append(':')
                .append(rcPass)
                .append("@127.0.0.1:")
                .append(port)
                .toString();
    }

    private static int nextAvailablePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setReuseAddress(true);
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            FLog.e(TAG, "nextAvailablePort: could not get port", e);
        }
        throw new IllegalStateException("No port available");
    }

    /**
     * Stop the rcd server
     */
    public void stopRcd() {
        if (null != rcd) {
            FLog.d(TAG, "Stopping Rclone");
            rcd.destroy();
        }
        if (null != jobsUpdateFuture) {
            jobsUpdateFuture.cancel(true);
        }
        stopped = true;
    }

    /**
     * Check if the server is still alive
     * @return
     */
    public boolean isAlive() {
        return !(null == rcd || stopped || !(getProcessState() == RUNNING));
    }

    public boolean hasCrashed() {
        return rcd != null && getProcessState() == ERROR;
    }

    /**
     * Retrieve the process state
     * @param process
     * @return 0: running, -1 exited normally, 1 exited with error
     */
    private @ProcessState int getProcessState() {
        try {
            return rcd.exitValue() != 0 ? ERROR : EXITED;
        } catch (IllegalThreadStateException e) {
            return RUNNING;
        }
    }

    /**
     * Convert a rcloneExplorer remote name into fs parameter format
     * @param remoteName
     * @return
     */
    private String remoteNameAsFs(String remoteName) {
        remoteName += ':';
        // TODO: figure out if this is required or vestigal
        if (':' != remoteName.charAt(remoteName.length() - 1)) {
            remoteName += ':';
        }
        return remoteName;
    }

    /**
     * Convert a rcloneExplorer remote name into "remote" path parameter format
     * @param remoteName
     * @param path
     * @return
     */
    private String pathAsPath(String remoteName, String path) {
        if (path.equals("//" + remoteName)) {
            path = path.substring(remoteName.length() + 2);
        }
        return path;
    }

    /**
     * Perform an RC call using a defined response type
     * @param method method name for rclone
     * @param params a method specific param definition
     * @param responseType reponse type used for deserialization
     * @param <T> call return value type (responseType)
     * @return call return value
     */
    private <T extends RcOpResponse> T performRcCall(String method, RcOpParam params, Class<T> responseType) {
        if ("main".equals(Thread.currentThread().getThreadGroup().getName())) {
            StrictMode.ThreadPolicy previous = StrictMode.getThreadPolicy();
            synchronized (mainThreadLock) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
                StrictMode.setThreadPolicy(policy);
                T result = performRcCall(method, params, null, responseType);
                StrictMode.setThreadPolicy(previous);
                return result;
            }
        } else {
            return performRcCall(method, params, null, responseType);
        }
    }

    /**
     * Perform an RC call using a Jackson typed reference (e.g. for Generics)
     * @param method method name for rclone
     * @param params a method specific param definition
     * @param typeReference Jackson generic type reference
     * @param <T> call return value type (responseType)
     * @return call return value
     */
    private <T> T performRcCall(String method, RcOpParam params, TypeReference<T> typeReference) {
        if ("main".equals(Thread.currentThread().getThreadGroup().getName())) {
            StrictMode.ThreadPolicy previous = StrictMode.getThreadPolicy();
            // allow network for vcp calls if the caller was dumb enough to use
            // a main thread.
            synchronized (mainThreadLock) {
                if (!method.equals("config/dump")) {
                    FLog.w(TAG, "Main thread used for rcd call, method=%s", new RuntimeException(), method);
                }
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
                StrictMode.setThreadPolicy(policy);
                T result = performRcCall(method, params, typeReference, null);
                StrictMode.setThreadPolicy(previous);
                return result;
            }
        } else {
            return performRcCall(method, params, typeReference, null);
        }
    }

    private OkHttpClient okHttpClient;

    private OkHttpClient prepareClient() {
        if (null == okHttpClient) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .authenticator((route, response) -> {
                        String credential = Credentials.basic(rcUser, rcPass);
                        return response.request().newBuilder()
                                .header("Authorization", credential).build();
                    });

            /*if (DEBUG) {
                okhttp3.logging.HttpLoggingInterceptor logging = new okhttp3.logging.HttpLoggingInterceptor();
                logging.level(okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS);
                builder.addInterceptor(logging);
            }*/

            okHttpClient = builder.build();
        }
        return okHttpClient;
    }

    // throws RcdOpException when the server returns an error message
    // do _not_ call directly
    private <T> T performRcCall(String method, RcOpParam params, TypeReference<T> typeReference, Class<T> responseType) {
        OkHttpClient client = prepareClient();
        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost")
                .port(port)
                .addPathSegments(method)
                .build();
        byte[] callParams;
        try {
            callParams = mapper.writeValueAsBytes(params);
        } catch (JsonProcessingException e) {
            ErrorResponse response = new ErrorResponse();
            response.operation = method;
            response.error = e.getMessage();
            throw new RcdOpException(response);
        }
        RequestBody body = RequestBody.create(callParams, JSON);
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            try {
                if (isErrorCode(response.code())) {
                    ErrorResponse error = mapper.readValue(response.body().byteStream(), ErrorResponse.class);
                    throw new RcdOpException(error);
                } else {
                    if (typeReference != null) {
                        return mapper.readValue(response.body().byteStream(), typeReference);
                    } else {
                        return mapper.readValue(response.body().byteStream(), responseType);
                    }
                }
            } catch (JsonProcessingException e) {
                FLog.e(TAG, "performRcCall: ", e);
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RcdIOException(e);
        }
    }

    private boolean isErrorCode(int code) {
        return 400 <= code;
    }

    ///
    /// Job Handling
    ///
    private static class RunJobStatusHandler implements Runnable {
        private final JobStatusHandler handler;
        private final JobStatusResponse response;

        public RunJobStatusHandler(JobStatusHandler handler, JobStatusResponse response) {
            this.handler = handler;
            this.response = response;
        }

        @Override
        public void run() {
            handler.handleJobStatus(response);
        }
    }

    final class PendingRcloneJobs implements Runnable {

        @Override
        public void run() {
            // block thread until job
            FLog.v(TAG, "pendingRcloneJobs: waiting for new job");
            try {
                Integer jobId = pendingJobs.take();
                pendingJobs.add(jobId);
            } catch (InterruptedException e) {
                // The containing runnable is scheduled as a periodic task to
                // monitor the status of pending rclone jobs. If the rcd
                // instance is shutdown while this job is in its blocking
                // phase (.take()), the job will be stopped by this exception.
                FLog.d(TAG, "pendingRcloneJobs: interrupted, exiting");
                return;
            }
            FLog.v(TAG, "pendingRcloneJobs: checking status");
            List<Integer> pending = new ArrayList<>();
            while (null != pendingJobs.peek()) {
                Integer jobId = pendingJobs.remove();
                FLog.v(TAG, "checking job: " + jobId);
                JobStatusResponse response = null;
                try {
                    response = getJobStatus(jobId);
                    lastStatus.put(jobId, response);
                } catch (RcdOpException e) {
                    FLog.e(TAG, "job error: ", e);
                    if ("job not found".equals(e.getError())) {
                        continue;
                    }
                }
                if (response.finished) {
                    JobStatusHandler handler = jobsHandlers.get(jobId);
                    if (null != handler) {
                        FLog.v(TAG, "job finished: " + jobId);
                        mainThread(handler, response);
                    }
                } else {
                    FLog.v(TAG, "job running: " + jobId);
                    pending.add(jobId);
                }
            }
            pendingJobs.addAll(pending);
            if (null != jobsUpdateHandler) {
                mainThread(() -> jobsUpdateHandler.onRcdJobsUpdate(lastStatus));
            }
        }
    }

    void mainThread(final JobStatusHandler handler, final JobStatusResponse response) {
        mainThread(new RunJobStatusHandler(handler, response));
    }

    void mainThread(Runnable runnable) {
        if(null == mainHandler) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        mainHandler.post(runnable);
    }

    public boolean hasPendingJobs() {
        return pendingJobs.size() > 0;
    }

    public interface JobsUpdateHandler {
        void onRcdJobsUpdate(SparseArray<JobStatusResponse> status);
    }

    //
    // Rclone rc API
    //

    public void cacheExpire(String directoryPath, boolean deleteData) {
        performRcCall("cache/expire", new CacheExpireRcOpParam(directoryPath, deleteData), EmptyOkResponse.class);
    }

    public void cacheFetch(String file, String chunks) {
        performRcCall("cache/fetch", new CacheFetchRcOpParam(chunks, file), EmptyOkResponse.class);
    }

    public void createConfig(String name, String type, HashMap<String, String> keyValue) {
        performRcCall("config/create", new ConfigCreateRcOpParam(name, type, keyValue), EmptyOkResponse.class);
    }

    public ListRemotesResponse configListremotes() {
        return performRcCall("config/listremotes", new NoParamRcOpParam(), ListRemotesResponse.class);
    }

    public Map<String, ConfigDumpRemote> configDump() {
        return performRcCall("config/dump", new NoParamRcOpParam(), new TypeReference<Map<String, ConfigDumpRemote>>() {
        });
    }

    public void isOnline() throws RcdIOException {
        performRcCall("rc/noopauth", new NoParamRcOpParam(), EmptyOkResponse.class);
    }

    public void sync(String srcFs, String dstFs, JobStatusHandler handler) {
        JobIdResponse response = performRcCall("sync/sync", new SyncRcOpParam(srcFs, dstFs), JobIdResponse.class);
        jobsHandlers.append(response.jobid, handler);
        pendingJobs.add(response.jobid);
    }

    public void copy(String srcFs, String dstFs, JobStatusHandler handler) {
        JobIdResponse response = performRcCall("sync/copy", new CopyRcOpParam(srcFs, dstFs), JobIdResponse.class);
        jobsHandlers.append(response.jobid, handler);
        pendingJobs.add(response.jobid);
    }

    public void copyFile(String srcRemoteName, String srcPath, String dstRemoteName, String dstPath, JobStatusHandler handler) {
        String srcFs = remoteNameAsFs(srcRemoteName);
        String dstFs = remoteNameAsFs(dstRemoteName);
        srcPath = pathAsPath(srcRemoteName, srcPath);
        dstPath = pathAsPath(dstRemoteName, dstPath);
        JobIdResponse response = performRcCall("operations/copyfile", new CopyFileRcOpParam(srcPath, srcFs, dstPath, dstFs), JobIdResponse.class);
        jobsHandlers.append(response.jobid, handler);
        pendingJobs.add(response.jobid);
    }

    public void move(String srcFs, String dstFs, JobStatusHandler handler) {
        JobIdResponse response = performRcCall("sync/move", new MoveRcOpParam(srcFs, dstFs, false), JobIdResponse.class);
        jobsHandlers.append(response.jobid, handler);
        pendingJobs.add(response.jobid);
    }

    public ListItem[] list(String remoteName, String path) {
        String fs = remoteNameAsFs(remoteName);
        path = pathAsPath(remoteName, path);
        ListRcOpResponse response = performRcCall("operations/list", new ListRcOpParam(fs, path), ListRcOpResponse.class);
        return response.list;
    }

    public JobStatusResponse getJobStatus(int jobId) {
        return performRcCall("job/status", new JobStatusRcOpParam(jobId), JobStatusResponse.class);
    }

    public JobListResponse listJobs() {
        return performRcCall("job/list", new NoParamRcOpParam(), JobListResponse.class);
    }

    public AboutResponse getStorageUsage(String remoteName) {
        return performRcCall("operations/about", new AboutRcOpParam(remoteNameAsFs(remoteName)), AboutResponse.class);
    }

    // TODO: figure out how this works - docu unclear!
    public void delete(String fs) {
        performRcCall("operations/delete", new DeleteRcOpParam(fs), EmptyOkResponse.class);
    }

    public void deleteFile(String remoteName, String path, JobStatusHandler handler) {
        String fs = remoteNameAsFs(remoteName);
        path = pathAsPath(remoteName, path);
        JobIdResponse response = performRcCall("operations/deletefile", new DeleteFileRcOpParam(fs, path), JobIdResponse.class);
        jobsHandlers.append(response.jobid, handler);
        pendingJobs.add(response.jobid);
    }

    public void purge(String remoteName, String path, JobStatusHandler handler) {
        String fs = remoteNameAsFs(remoteName);
        path = pathAsPath(remoteName, path);
        JobIdResponse response = performRcCall("operations/purge", new PurgeRcOpParam(fs, path), JobIdResponse.class);
        jobsHandlers.append(response.jobid, handler);
        pendingJobs.add(response.jobid);
    }

    public FsInfoRcOpResponse getFsInfo(String remoteName) {
        String fs = remoteNameAsFs(remoteName);
        return performRcCall("operations/fsinfo", new FsInfoRcOpParam(fs), FsInfoRcOpResponse.class);
    }

    public void mkDir(String remoteName, String path) {
        String fs = remoteNameAsFs(remoteName);
        path = pathAsPath(remoteName, path);
        performRcCall("operations/mkdir", new MkDirRcOpParam(fs, path), EmptyOkResponse.class);
    }

    public void moveFile(String srcRemoteName, String srcPath, String dstRemoteName, String dstPath, JobStatusHandler handler) {
        String srcFs = remoteNameAsFs(srcRemoteName);
        String srcRemote = pathAsPath(srcRemoteName, srcPath);
        String dstFs = remoteNameAsFs(dstRemoteName);
        String dstRemote = pathAsPath(dstRemoteName, dstPath);
        JobIdResponse response = performRcCall("operations/movefile", new MoveFileRcOpParam(srcFs, srcRemote, dstFs, dstRemote), JobIdResponse.class);
        pendingJobs.add(response.jobid);
        jobsHandlers.append(response.jobid, handler);
    }

    public String getPublicLink(String remoteName, String path) {
        String fs = remoteNameAsFs(remoteName);
        path = pathAsPath(remoteName, path);
        PublicLinkRcOpResponse response = performRcCall("operations/publiclink", new PublicLinkRcOpParam(fs, path), PublicLinkRcOpResponse.class);
        return response.url;
    }

    public void rmDir(String remoteName, String path) {
        String fs = remoteNameAsFs(remoteName);
        path = pathAsPath(remoteName, path);
        performRcCall("operations/rmdir", new RmDirRcOpParam(fs, path), EmptyOkResponse.class);
    }

    public void rmDirs(String remoteName, String path) {
        String fs = remoteNameAsFs(remoteName);
        path = pathAsPath(remoteName, path);
        performRcCall("operations/rmdirs", new RmDirsRcOpParam(fs, path), EmptyOkResponse.class);
    }

    ///
    /// Operation Parameter Classes
    ///

    private static class NoParamRcOpParam implements RcOpParam {
    }

    private static class CacheExpireRcOpParam implements RcOpParam {
        String remote;
        boolean withData;

        public CacheExpireRcOpParam(String remote, boolean withData) {
            this.remote = remote;
            this.withData = withData;
        }
    }

    private static class CacheFetchRcOpParam implements RcOpParam {
        String chunks;
        String file;

        public CacheFetchRcOpParam(String chunks, String file) {
            this.chunks = chunks;
            this.file = file;
        }
    }

    private static class ConfigCreateRcOpParam implements RcOpParam {
        String name;
        String type;
        HashMap<String, String> parameters;

        public ConfigCreateRcOpParam(String name, String type, HashMap<String, String> parameters) {
            this.name = name;
            this.type = type;
            this.parameters = parameters;
        }
    }

    private static class ConfigDeleteRcOpParam implements RcOpParam {
        String name;

        public ConfigDeleteRcOpParam(String name) {
            this.name = name;
        }
    }

    private static class ConfigGetRcOpParam implements RcOpParam {
        String name;

        public ConfigGetRcOpParam(String name) {
            this.name = name;
        }
    }

    private static class SyncRcOpParam implements RcloneRcd.RcOpParam {
        String srcFs;
        String dstFs;
        boolean _async = true;

        public SyncRcOpParam(String srcFs, String dstFs) {
            this.srcFs = srcFs;
            this.dstFs = dstFs;
        }
    }

    private static class CopyRcOpParam implements RcloneRcd.RcOpParam {
        String srcFs;
        String dstFs;
        boolean _async = true;

        public CopyRcOpParam(String srcFs, String dstFs) {
            this.srcFs = srcFs;
            this.dstFs = dstFs;
        }
    }

    private static class CopyFileRcOpParam implements RcloneRcd.RcOpParam {
        String srcFs;
        String srcRemote;
        String dstFs;
        String dstRemote;
        boolean _async = true;

        public CopyFileRcOpParam(String srcRemote, String srcFs, String dstRemote, String dstFs) {
            this.srcRemote = srcRemote;
            this.srcFs = srcFs;
            this.dstRemote = dstRemote;
            this.dstFs = dstFs;
        }
    }

    private static class CopyUrlRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;
        String url;
        boolean _async = true;

        public CopyUrlRcOpParam(String fs, String remote, String url) {
            this.fs = fs;
            this.remote = remote;
            this.url = url;
        }
    }

    private static class MoveRcOpParam implements RcloneRcd.RcOpParam {
        String srcFs;
        String dstFs;
        boolean deleteEmptySrcDirs;
        boolean _async = true;

        public MoveRcOpParam(String srcFs, String dstFs, boolean deleteEmptySrcDirs) {
            this.srcFs = srcFs;
            this.dstFs = dstFs;
            this.deleteEmptySrcDirs = deleteEmptySrcDirs;
        }
    }

    private static class MoveFileRcOpParam implements RcloneRcd.RcOpParam {
        String srcFs;
        String srcRemote;
        String dstFs;
        String dstRemote;
        boolean _async = true;

        public MoveFileRcOpParam(String srcFs, String srcRemote, String dstFs, String dstRemote) {
            this.srcFs = srcFs;
            this.srcRemote = srcRemote;
            this.dstFs = dstFs;
            this.dstRemote = dstRemote;
        }
    }

    // TODO: doc unclear
    private static class DeleteRcOpParam implements RcloneRcd.RcOpParam {
        String fs;

        public DeleteRcOpParam(String fs) {
            this.fs = fs;
        }
    }

    private static class DeleteFileRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;
        boolean _async = true;

        public DeleteFileRcOpParam(String fs, String remote) {
            this.fs = fs;
            this.remote = remote;
        }
    }

    private static class FsInfoRcOpParam implements RcloneRcd.RcOpParam {
        String fs;

        public FsInfoRcOpParam(String fs) {
            this.fs = fs;
        }
    }

    private static class ListRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;

        public ListRcOpParam(String fs, String remote) {
            this.fs = fs;
            this.remote = remote;
        }
    }

    private static class MkDirRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;

        public MkDirRcOpParam(String fs, String remote) {
            this.fs = fs;
            this.remote = remote;
        }
    }

    private static class RmDirRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;

        public RmDirRcOpParam(String fs, String remote) {
            this.fs = fs;
            this.remote = remote;
        }
    }

    private static class PurgeRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;
        boolean _async = true;

        public PurgeRcOpParam(String fs, String remote) {
            this.fs = fs;
            this.remote = remote;
        }
    }

    private static class RmDirsRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;
        boolean leaveRoot;

        public RmDirsRcOpParam(String fs, String remote) {
            this.fs = fs;
            this.remote = remote;
            this.leaveRoot = false;
        }

        public RmDirsRcOpParam(String fs, String remote, boolean leaveRoot) {
            this.fs = fs;
            this.remote = remote;
            this.leaveRoot = leaveRoot;
        }
    }

    private static class PublicLinkRcOpParam implements RcloneRcd.RcOpParam {
        String fs;
        String remote;

        public PublicLinkRcOpParam(String fs, String remote) {
            this.fs = fs;
            this.remote = remote;
        }
    }

    private static class JobStatusRcOpParam implements RcloneRcd.RcOpParam {
        int jobid;

        public JobStatusRcOpParam(int jobid) {
            this.jobid = jobid;
        }
    }

    private static class JobStopRcOpParam implements RcloneRcd.RcOpParam {
        int jobid;

        public JobStopRcOpParam(int jobid) {
            this.jobid = jobid;
        }
    }

    private static class AboutRcOpParam implements RcloneRcd.RcOpParam {
        String fs;

        public AboutRcOpParam(String fs) {
            this.fs = fs;
        }
    }

    private static class CleanupRcOpParam implements RcloneRcd.RcOpParam {
        String fs;

        public CleanupRcOpParam(String fs) {
            this.fs = fs;
        }
    }

    /**
     * Marker interface for RC Parameters
     */
    interface RcOpParam {
    }

    /**
     * Marker interface for RC Responses
     */
    interface RcOpResponse {
    }

    public interface JobStatusHandler {
        void handleJobStatus(JobStatusResponse jobStatusResponse);
    }

    public static class RcdOpException extends RuntimeException {
        private ErrorResponse error;

        public RcdOpException(ErrorResponse error) {
            super("Error when executing " + error.operation);
            this.error = error;
        }

        private RcdOpException() {
        }

        public String getError() {
            return error.getError();
        }
    }

    public static class RcdIOException extends RcdOpException {
        private IOException exception;

        public RcdIOException(IOException exception) {
            this.exception = exception;
        }

        @Override
        public String getError() {
            return exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
    }

    ///
    /// Operation Response DTOs
    ///
    private static class GenericResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmptyOkResponse extends GenericResponse implements RcOpResponse {
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorResponse extends GenericResponse implements RcOpResponse {

        @JsonProperty("error")
        String error;

        @JsonProperty("path")
        String operation;

        @JsonProperty("status")
        int status;

        public String getError() {
            return error;
        }

        public String getOperation() {
            return operation;
        }

        public int getStatus() {
            return status;
        }
    }

    public static class ListRemotesResponse implements RcOpResponse {
        String[] remotes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfigDumpRemote {
        String type;
    }

    private static class AboutResponse implements RcOpResponse {
        long free;
        long total;
        long trashed;
        long used;
    }

    // Example
    // ===
    // {
    //	"duration": 0.514823698,
    //	"endTime": "2021-05-11T22:21:41.780060917Z",
    //	"error": "mkdir /Alarms: read-only file system",
    //	"finished": true,
    //	"group": "job/1",
    //	"id": 1,
    //	"output": {},
    //	"startTime": "2021-05-11T22:21:41.265237323Z",
    //	"success": false
    //}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobStatusResponse implements RcOpResponse {
        public int id;
        public boolean finished;
        public boolean success;
        @JsonDeserialize(using = Rfc3339Deserializer.class)
        public long startTime;
        @JsonDeserialize(using = Rfc3339Deserializer.class)
        public long endTime;
        public String error;
        public Object output;
        public String progress;
    }

    public static class JobListResponse implements RcOpResponse {
        public int[] jobids;
    }

    private static class JobIdResponse implements RcOpResponse {
        int jobid;
    }

    private static class ListRcOpResponse implements RcOpResponse {
        ListItem[] list;
    }

    /**
     * Less memory overhead than {@link ca.pkay.rcloneexplorer.Items.FileItem} by removing pregenerated formatting
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListItem {
        @JsonProperty("Path")
        String path;

        @JsonProperty("Name")
        String name;

        @JsonProperty("MimeType")
        String mimeType;

        @JsonProperty("Size")
        long size;

        @JsonProperty("ModTime")
        @JsonDeserialize(using = Rfc3339Deserializer.class)
        long lastModified;

        @JsonProperty("IsDir")
        boolean isDir;
    }

    private static class FsInfoRcOpResponse implements RcOpResponse {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class FsInfoFeatures {
            @JsonProperty("About")
            boolean about;

            @JsonProperty("Copy")
            boolean copy;

            @JsonProperty("DirMove")
            boolean dirMove;

            @JsonProperty("Move")
            boolean move;

            @JsonProperty("PublicLink")
            boolean publicLink;

            @JsonProperty("PutStream")
            boolean putStream;

            @JsonProperty("WrapFs")
            boolean wrapFs;
        }

        @JsonProperty("Features")
        FsInfoFeatures features;

        @JsonProperty("Hashes")
        String[] hashes;

        @JsonProperty("Name")
        String name;

        @JsonProperty("Precision")
        int precision;

        @JsonProperty("String")
        String inLogsAs;
    }

    private static class PublicLinkRcOpResponse implements RcOpResponse {
        String url;
    }
}
