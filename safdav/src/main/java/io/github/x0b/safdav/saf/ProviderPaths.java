package io.github.x0b.safdav.saf;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.Nullable;

import io.github.x0b.safdav.file.ItemNotFoundException;
import io.github.x0b.safdav.file.SafItem;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProviderPaths {

    private static volatile ConcurrentHashMap<String, Uri> urisById;
    private static volatile ConcurrentHashMap<Uri, String> idsByUri;

    public static final String ANDROID_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";

    private final Context context;
    // stores permissions by their name
    private static final HashMap<String, Uri> permissionsByPath = new HashMap<>();

    public ProviderPaths(Context context) {
        this.context = context;
    }

    /**
     * Get the root folder (/). ProviderPaths ensures that underlying paths can be resolved using ContentProvider
     * URIs. In a future version, flattening may also be applied.
     * @return a SafItem that represents the root folder
     */
    public SafItem getSafRootDirectory() {
        List<UriPermission> permissions = context.getContentResolver().getPersistedUriPermissions();
        return new SafPermissionItem(permissions);
    }

    /**
     * Create a mapping between a DAV path and a corresponding permission item. This path will be
     * later used to look up the correct permission to use for accessing that storage volume.
     * @param uri
     * @return
     */
    public static String getPathForUri(Uri uri){
        if (!ANDROID_PROVIDER_AUTHORITY.equals(uri.getAuthority())) {
            return mapUriThirdParty(uri);
        }
        return Uri.encode(getAndroidPermissionName(uri)) + "/";
    }

    private static synchronized String getAndroidPermissionName(Uri uri) {
        String path = getNormalizedPath(uri);
        path = path.substring(0, path.length() - 1).replace("/", " - ");
        if ("primary".equals(path)) {
            path = Environment.isExternalStorageRemovable() ? "SD Card" : "Internal Storage";
        }
        String name = path;
        int suffix = 2;
        while (permissionsByPath.containsKey(name) && !uri.equals(permissionsByPath.get(name))) {
            name = path + " (" + suffix++ + ")";
        }
        permissionsByPath.put(name, uri);
        return name;
    }

    /**
     * Translates a request path into a requestable contest uri. This process assumes that <br>
     * - a hierarchical filesystem is accessed<br>
     * - this system can be mapped to urls just copying the path as sub-url path<br>
     * - the transformed url might represent a not (yet) existing object!<br>
     *  Specifically, it is only tested using com.android.externalstorage.documents
     * @param requestUri a mapped path, e.g. {@code /primary/DCIM}
     * @return an {@link Uri} representing the path as tree uri, e.g. {@code content://com.android.externalstorage.documents/tree/primary:DCIM}
     */
    public Uri getUriByMappedPath(String requestUri) {
        if (requestUri == null || requestUri.length() < 2 || requestUri.charAt(0) != '/'
                || requestUri.indexOf('\\') >= 0 || requestUri.indexOf('\0') >= 0) {
            throw new ItemNotFoundException();
        }
        String path = requestUri.substring(1);
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new ItemNotFoundException();
            }
        }
        List<UriPermission> grants = context.getContentResolver().getPersistedUriPermissions();
        Uri legacyGrant = null;
        String legacyPath = "";
        for (UriPermission grant : grants) {
            if (!grant.isReadPermission() && !grant.isWritePermission()) continue;
            Uri uri = grant.getUri();
            if (!ANDROID_PROVIDER_AUTHORITY.equals(uri.getAuthority())) continue;
            String alias = getAndroidPermissionName(uri);
            if (path.equals(alias) || path.startsWith(alias + "/")) {
                return appendDescendants(uri, path.substring(alias.length()));
            }
            // Accept the old volume/subdirectory path for existing bookmarks and tasks.
            String normalized = getNormalizedPath(uri);
            if ((path.equals(normalized.substring(0, normalized.length() - 1))
                    || path.startsWith(normalized)) && normalized.length() > legacyPath.length()) {
                legacyGrant = uri;
                legacyPath = normalized;
            }
        }
        if (legacyGrant != null) {
            return appendDescendants(legacyGrant, path.substring(legacyPath.length() - 1));
        }

        Uri thirdParty = getUriThirdParty(requestUri);
        if (thirdParty != null) {
            for (UriPermission grant : grants) {
                Uri uri = grant.getUri();
                if (thirdParty.equals(uri) || thirdParty.toString().startsWith(uri.toString() + "/")) {
                    return thirdParty;
                }
            }
        }
        throw new ItemNotFoundException();
    }

    private static Uri appendDescendants(Uri root, String descendants) {
        Uri.Builder builder = root.buildUpon();
        for (String segment : descendants.split("/")) {
            if (!segment.isEmpty()) builder.appendPath(segment);
        }
        return builder.build();
    }

    /**
     * Converts a permission uri into a path for the top level folder structure
     *
     * content://com.android.externalstorage.documents/tree/primary:                        primary_12345678
     * content://com.paragon_software.documentproviderserver.documents/tree/root/DISK_IMG   DISK_IMG_34567890
     *
     * @param uri
     * @return
     */
    public static String getNormalizedPath(Uri uri){
        String path = uri.getPath().replace(":", "/");
        int begin = !path.contains("/tree/") ? 0 : "/tree/".length();
        int end = path.lastIndexOf(':') == path.length()-1 ? path.length()-1 : path.length();
        path = path.substring(begin, end);
        if(path.length()-1 != path.lastIndexOf('/')) {
            path = path + '/';
        }
        return path;
    }

    private static void initUriMaps() {
        if (null == idsByUri) {
            synchronized (ProviderPaths.class) {
                if(null == idsByUri) {
                    idsByUri = new ConcurrentHashMap<>();
                    urisById = new ConcurrentHashMap<>();
                }
            }
        }
    }

    private static String mapUriThirdParty(Uri uri) {
        initUriMaps();
        synchronized (idsByUri) {
            if(!idsByUri.containsKey(uri)) {
                String uriId = uri.getAuthority() + " (" + UUID.randomUUID().toString() + ")"; //uri.getPath().replace("/", "_")
                idsByUri.put(uri, uriId);
                urisById.put(uriId, uri);
            }
            return Uri.encode(idsByUri.get(uri)) + "/";
        }
    }

    private static @Nullable Uri getUriThirdParty(String requestUri) {
        int segmentEnd = requestUri.indexOf('/', 1);
        if (-1 == segmentEnd && requestUri.length() >= 2) {
            segmentEnd = requestUri.length();
        }
        String permissionId = requestUri.substring(1, segmentEnd);
        initUriMaps();
        synchronized (idsByUri) {
            if(urisById.containsKey(permissionId)) {
                Uri uri = urisById.get(permissionId);
                if (requestUri.length() >= segmentEnd + 1) {
                    return appendDescendants(uri, requestUri.substring(segmentEnd + 1));
                } else {
                    return uri;
                }
            }
            return null;
        }
    }
}
