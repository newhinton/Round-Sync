package io.github.x0b.safdav.saf;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import io.github.x0b.safdav.file.FileAccessError;
import io.github.x0b.safdav.file.ItemAccess;
import io.github.x0b.safdav.file.ItemExistsException;
import io.github.x0b.safdav.file.ItemNotFoundException;
import io.github.x0b.safdav.file.SafException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * A variant of an access provider using lower level DocumentsContract and ContentResolver#query apis.
 * Should be faster than the DocumentFile based access methods.
 *
 * Especially for directory lists, where all required parameters are queried at once for all files rather than letting
 * the framework create DocumentFiles for you which will query the content resolver for _each and every_ file property
 * on access, e.g. serialization query time == t_single-cell-query * num_files * num_properties.
 */
public class DocumentsContractAccess implements ItemAccess<SafFastItem> {

    private static final String TAG = "DocumentsContractAccess";
    private final Context context;
    private final SafFileAccess sfa;

    public DocumentsContractAccess(Context context) {
        this.context = context;
        this.sfa = new SafFileAccess(context);
    }

    @Override
    public SafFastItem createFile(Uri uri, String name, String mimeType, InputStream content, long len) {
        Uri parentUri = getParent(uri);
        try {
            Uri fileUri = DocumentsContract.createDocument(context.getContentResolver(), parentUri,
                    mimeType, getDisplayName(uri));
            sfa.writeFile(fileUri, content, len);
            //return sfa.readMeta(fileUri);
            return readMeta(uri);
        } catch (FileNotFoundException e) {
            throw new ItemNotFoundException(e);
        }
    }

    @Override
    public void createDirectory(Uri uri) {
        try {
            readMeta(uri);
        } catch (ItemNotFoundException e) {
            try {
                DocumentsContract.createDocument(context.getContentResolver(), getParent(uri),
                        DocumentsContract.Document.MIME_TYPE_DIR, getDisplayName(uri));
            } catch (FileNotFoundException en) {
                throw new ItemNotFoundException(en);
            }
            return;
        }
        throw new ItemExistsException();
    }

    @Override
    public void deleteItem(Uri uri) {
        try {
            DocumentsContract.deleteDocument(context.getContentResolver(), buildHierarchicalDocumentsUri(uri));
        } catch (FileNotFoundException e) {
            throw new ItemNotFoundException(e);
        }
    }

    @Override
    public void copyItem(Uri srcUri, Uri dstUri) {
        sfa.copyItem(srcUri, dstUri);
    }

    @Override
    public void moveItem(Uri srcStubUri, Uri targetStubUri) {
        Uri srcUri = buildHierarchicalDocumentsUri(srcStubUri);
        Uri srcParentUri = getParent(srcStubUri);
        Uri dstParentUri = getParent(targetStubUri);

        if (srcParentUri.equals(dstParentUri)) {
            renameItem(srcStubUri, targetStubUri);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                DocumentsContract.moveDocument(context.getContentResolver(), srcUri, srcParentUri, dstParentUri);
            } catch (FileNotFoundException e) {
                throw new ItemNotFoundException();
            }
        } else {
            sfa.moveItem(srcStubUri, targetStubUri);
        }
    }

    @Override
    public void renameItem(Uri item, Uri target) {
        Uri documentUri = buildHierarchicalDocumentsUri(item);
        try {
            DocumentsContract.renameDocument(context.getContentResolver(), documentUri, getDisplayName(target));
        } catch (FileNotFoundException e) {
            throw new ItemNotFoundException();
        }
    }

    @Override
    public List<SafFastItem> list(Uri treeUri) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri childDocsUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(treeUri));
        final ArrayList<SafFastItem> results = new ArrayList<>();

        try (Cursor cursor = contentResolver.query(childDocsUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
        }, null, null, null)) {
            if (null == cursor) {
                return results;
            }
            while (cursor.moveToNext()) {
                final String documentId = cursor.getString(0);
                String mimeType = cursor.getString(2);
                boolean isDirectory = false;
                if (mimeType.equals(DocumentsContract.Document.MIME_TYPE_DIR)) {
                    mimeType = "inode/directory";
                    isDirectory = true;
                }
                SafFastItem item = new SafFastItem(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        cursor.getString(1),
                        mimeType,
                        cursor.getLong(3),
                        cursor.getLong(4),
                        isDirectory
                );
                results.add(item);
            }
        } catch (Exception e) {
            Log.w("DocumentsContractAccess", "Failed query: " + e);
        }
        return results;
    }

    @Override
    public InputStream readFile(SafFastItem documentUri) {
        try {
            return context.getContentResolver().openInputStream(documentUri.getUri());
        } catch (FileNotFoundException e) {
            throw new ItemNotFoundException(e);
        }
    }

    @Override
    public SafFastItem readMeta(Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri docUri = buildHierarchicalDocumentsUri(uri);
        try (Cursor cursor = contentResolver.query(docUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
        }, null, null, null)) {
            if (null == cursor) {
                throw new ItemNotFoundException();
            }
            if (cursor.moveToNext()) {
                final String documentId = cursor.getString(0);
                String mimeType = cursor.getString(2);
                if (null == mimeType) {
                    mimeType = fixMimeType(uri);
                }
                boolean isDirectory = false;
                List<SafFastItem> childItems = null;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    mimeType = "inode/directory";
                    isDirectory = true;
                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(buildHierarchicalDocumentsUri(uri), documentId);
                    childItems = list(childUri);
                }
                return new SafFastItem(
                        DocumentsContract.buildDocumentUriUsingTree(buildHierarchicalDocumentsUri(uri), documentId),
                        cursor.getString(1),
                        mimeType,
                        cursor.getLong(3),
                        cursor.getLong(4),
                        isDirectory,
                        childItems
                );
            }
        } catch (IllegalArgumentException e) {
            throw new ItemNotFoundException(e);
        } catch (Exception e) {
            Log.w("DocumentsContractAccess", "Failed query: " + e);
            throw new FileAccessError(e);
        }
        throw new ItemNotFoundException();
    }

    private String fixMimeType(Uri uri) {
        // Ensure that seafile libraries are handled like a folder.
        // On the initial query, DocumentFile.isDirectory() returns false.
        String mimeType = "application/octet-stream";
        if ("com.seafile.seadroid2".equals(uri.getAuthority())) {
            DocumentFile file = DocumentFile.fromTreeUri(context, buildHierarchicalDocumentsUri(uri));
            if (null != file && !file.isFile()) {
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
            }
        }
        return mimeType;
    }

    @Override
    public void writeFile(Uri documentUri, InputStream stream, long len) {
        // Write directly through the ContentResolver with a 64 KB buffer instead of delegating
        // to SafFileAccess, avoiding an extra indirection and a much smaller copy buffer.
        try (OutputStream os = context.getContentResolver().openOutputStream(documentUri)) {
            if (os == null) {
                throw new FileAccessError();
            }
            byte[] buffer = new byte[64 * 1024];
            int blen = stream.read(buffer);
            long remaining = len;
            while (blen != -1) {
                os.write(buffer, 0, blen);
                remaining -= blen;
                if (remaining < 1) {
                    break;
                }
                blen = stream.read(buffer);
            }
            os.flush();
        } catch (FileNotFoundException e) {
            throw new ItemNotFoundException(e);
        } catch (IOException e) {
            throw new FileAccessError(e);
        }
    }

    private Uri getParent(Uri uri) {
        return buildHierarchicalDocumentsUri(uri, true);
    }

    private Uri buildHierarchicalDocumentsUri(Uri uri) {
        return buildHierarchicalDocumentsUri(uri, false);
    }

    // Note: this is modelled to observed behavior, because the
    // DocumentsContract#fromXXUri APIs don't really work well
    private Uri buildHierarchicalDocumentsUri(Uri uri, boolean buildParent) {
        if(!ProviderPaths.ANDROID_PROVIDER_AUTHORITY.equals(uri.getAuthority())) {
            if (buildParent) {
                return DocumentFile.fromTreeUri(context, uri).getParentFile().getUri();
            } else {
                // This is a thirdparty provider. No assumptions regarding URL
                // structure are possible. Instead, follow documented semantics.
                return treeWalkCompat(uri);
            }
        }
        String path = uri.getPath();
        int treeIndex = path.indexOf("/tree/");
        if (0 != treeIndex) {
            throw new SafException("Unsupported DocumentsProvider: " + uri.toString());
        }
        int sep;
        if (-1 != (sep = path.indexOf(':', 6)) || -1 != (sep = path.indexOf('/', 6))) {
            try {
                String volume = URLEncoder.encode(path.substring(6, sep + 1), "UTF-8");
                String pathRaw = path.substring(sep + 1);
                String mediaPath = pathRaw.replaceAll("/", "%2F");

                if (buildParent) {
                    int parentSep = pathRaw.lastIndexOf('/');
                    // Check: format assumption - for collections, adjust path
                    if (parentSep == pathRaw.length() - 1) {
                        parentSep = pathRaw.lastIndexOf('/', parentSep - 1);
                    }
                    // Check: is there at least one subfolder?
                    if (parentSep != -1) {
                        mediaPath = pathRaw.substring(0, parentSep).replaceAll("/", "%2F");
                    } else {
                        mediaPath = "";
                    }
                }
                return Uri.parse(uri.getScheme() + "://" + uri.getAuthority() + "/tree/" + volume + "/document/" + volume + mediaPath);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("UTF-8 not available");
            }
        }
        throw new SafException("Unsupported DocumentsProvider: " + uri.toString());
    }

    // /tree/primary:DCIM
    // /tree/primary:DCIM/IMG_1.jpg
    private String getDisplayName(Uri uri) {
        List<String> segments = uri.getPathSegments();
        String displayName;
        String path = uri.getPath();
        int rootSep;
        if (segments.size() >= 3) {
            displayName = segments.get(segments.size() - 1);
        } else if ((rootSep = path.indexOf(':', 6)) != -1 || (rootSep = path.indexOf('/', 6)) != -1) {
            displayName = path.substring(rootSep + 1);
        } else {
            displayName = segments.get(segments.size() - 1);
        }

        if (displayName.lastIndexOf('/') == displayName.length() - 1) {
            displayName = displayName.substring(0, displayName.length() - 1);
        }

        return displayName;
    }

    /**
     * Get the display name, using standard contract APIs.
     * @param uri document uri
     * @return display name
     */
    private String getDisplayNameCompat(Uri uri) {
        String[] projection = new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

   private  Uri treeWalkCompat(Uri uri) {
        Uri baseUri = DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), DocumentsContract.getTreeDocumentId(uri));
        Uri subFolder = DocumentFile.fromTreeUri(context, baseUri).getUri();
        String path = uri.getPath();
        String mediaPath = path.substring(baseUri.getPath().length());
        String[] directories = mediaPath.split("/");
        for (String directory : directories) {
            if("".equals(directory)) {
                continue;
            }
            List<SafFastItem> items = list(subFolder);
            for(SafFastItem item : items) {
                if (directory.equals(item.getName())) {
                    subFolder = item.getUri();
                    continue;
                }
            }
        }
        return subFolder;
    }
}
