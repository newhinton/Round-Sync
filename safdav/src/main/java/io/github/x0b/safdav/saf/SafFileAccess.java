package io.github.x0b.safdav.saf;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.documentfile.provider.DocumentFile;
import io.github.x0b.safdav.file.FileAccessError;
import io.github.x0b.safdav.file.ItemAccess;
import io.github.x0b.safdav.file.ItemExistsException;
import io.github.x0b.safdav.file.ItemNotFoundException;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses {@link DocumentFile} APIs to access files. This makes it incredibly
 * slow, but highly compatible against any DocumentProvider.
 */
public class SafFileAccess implements ItemAccess<SafFileItem> {

    private static final String TAG = "SafAccess";

    private final Context context;

    public SafFileAccess(Context context) {
        this.context = context;
    }

    public List<SafFileItem> list(Uri treeUri) {
        DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);
        List<SafFileItem> items = new ArrayList<>();
        for (DocumentFile file : tree.listFiles()) {
            items.add(new SafFileItem(file));
        }
        return items;
    }

    @Override
    public InputStream readFile(SafFileItem documentUri) {
        Uri uri = documentUri.getFile().getUri();
        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor parcelFileDescriptor = resolver.openFileDescriptor(uri, "r");) {
            if (null == parcelFileDescriptor) {
                throw new FileAccessError();
            }
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            return new FileInputStream(fileDescriptor);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public SafFileItem readMeta(Uri uri) {
        SafFileItem item;
        if (uri.getPath().endsWith(":")) {
            // Only Root URIs can be used to build from tree
            item = new SafFileItem(DocumentFile.fromTreeUri(context, uri));
        } else {
            DocumentFile file = treeWalk(uri, false);
            if (null == file) {
                throw new ItemNotFoundException();
            }
            item = new SafFileItem(file);
        }
        return item;
    }

    @Override
    public void writeFile(Uri documentUri, InputStream stream, long len) {
        try (OutputStream os = context.getContentResolver().openOutputStream(documentUri);) {
            if (os == null) {
                throw new FileAccessError();
            }
            try {
                byte[] buffer = new byte[64 * 1024];
                int blen = stream.read(buffer);
                while (blen != -1) {
                    len -= blen;
                    os.write(buffer, 0, blen);
                    // don't read past length
                    if (len < 1) {
                        break;
                    }
                    blen = stream.read(buffer);
                }
            } catch (IOException e) {
                throw new FileAccessError(e);
            }

        } catch (FileNotFoundException e) {
            throw new ItemNotFoundException();
        } catch (IOException e) {
            throw new FileAccessError(e);
        }
    }

    public SafFileItem createFile(Uri uri, String name, String mimeType) {
        DocumentFile parent = treeWalk(uri, true);
        DocumentFile file = parent.createFile(mimeType, name);
        if (file == null) {
            throw new FileAccessError();
        }

        return new SafFileItem(file);
    }

    @Override
    public SafFileItem createFile(Uri uri, String name, String mimeType, InputStream content, long len) {
        SafFileItem item = createFile(uri, name, mimeType);
        writeFile(item.getFile().getUri(), content, len);
        return item;
    }

    @Override
    public void createDirectory(Uri uri) {
        // TODO: technically a WebDAV violation (fail with 409 Conflict missing)
        //  https://tools.ietf.org/html/rfc4918#section-9.3
        DocumentFile file = treeCreate(uri);
        if (null == file) {
            throw new FileAccessError();
        }
    }

    @Override
    public void deleteItem(Uri uri) {
        DocumentFile file = treeWalk(uri, false);
        if (file == null) {
            throw new ItemNotFoundException();
        } else if (!file.delete()) {
            throw new FileAccessError();
        }
    }

    @Override
    public void copyItem(Uri srcUri, Uri dstUri) {
        DocumentFile srcFile = treeWalk(srcUri, false);
        InputStream srcStream = readFile(new SafFileItem(srcFile));
        createFile(dstUri, dstUri.getLastPathSegment(), srcFile.getType(), srcStream, srcFile.length());
    }

    @Override
    public void moveItem(Uri srcUri, Uri dstUri) {
        DocumentFile srcFile = treeWalk(srcUri, false);
        DocumentFile dstDir = treeWalk(dstUri, true);
        if (srcFile == null || dstDir == null) {
            throw new ItemNotFoundException();
        }
        copyItem(srcUri, dstUri);
        srcFile.delete();
    }

    @Override
    public void renameItem(Uri item, Uri target) {

    }

    private DocumentFile buildRoot(Uri uri) {
        Uri.Builder builder = new Uri.Builder();
        String path = uri.getPath();
        Uri root = builder.scheme(uri.getScheme())
                .authority(uri.getAuthority())
                .path(path.substring(0, path.indexOf(':') + 1))
                .build();
        return DocumentFile.fromTreeUri(context, root);
    }

    DocumentFile treeWalk(Uri uri, boolean parentDirIfNotExist) {
        DocumentFile subFolder = buildRoot(uri);
        String path = uri.getPath();
        String mediaPath = path.substring(path.indexOf(':') + 1);
        String[] directories = mediaPath.split("/");
        for (String directory : directories) {
            DocumentFile file = subFolder.findFile(directory);
            if (file == null && parentDirIfNotExist) {
                return subFolder;
            }
            subFolder = file;
        }
        return subFolder;
    }

    private DocumentFile treeCreate(Uri uri) {
        DocumentFile subFolder = buildRoot(uri);
        String path = uri.getPath();
        String mediaPath = path.substring(path.indexOf(':') + 1);
        if ("".equals(mediaPath)) {
            throw new ItemExistsException();
        }
        String[] directories = mediaPath.split("/");
        for (String directory : directories) {
            DocumentFile file = subFolder.findFile(directory);
            if (file == null) {
                file = subFolder.createDirectory(directory);
            }
            subFolder = file;
        }
        return subFolder;
    }
}
