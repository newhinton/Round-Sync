package io.github.x0b.safdav;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import io.github.x0b.safdav.file.FileAccessError;
import io.github.x0b.safdav.file.ItemAccess;
import io.github.x0b.safdav.file.ItemExistsException;
import io.github.x0b.safdav.file.ItemNotFoundException;
import io.github.x0b.safdav.file.SafConstants;
import io.github.x0b.safdav.file.SafException;
import io.github.x0b.safdav.file.SafItem;
import io.github.x0b.safdav.saf.DocumentsContractAccess;
import io.github.x0b.safdav.saf.ProviderPaths;
import io.github.x0b.safdav.xml.XmlResponseSerialization;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SafDAVServer extends NanoHTTPD {

    private static final String TAG = "SafDAVServer";
    private static final String hostname = "localhost";
    private final ItemAccess itemAccess;
    private final XmlResponseSerialization respSerializer;
    private ProviderPaths paths;
    private String requiredAuthHeader;

    /**
     * SafDAV binds to localhost. You must provide your own port availability checking mechanism
     * Note that authentication is currently not supported.
     * @param port the device port to listen on
     */
    @Deprecated
    public SafDAVServer(int port, Context context) {
        super(hostname, port);
        itemAccess = new DocumentsContractAccess(context);
        this.paths = new ProviderPaths(context);
        respSerializer = new XmlResponseSerialization();
    }

    /**
     * SafDAV binds to localhost. You must provide your own port availability checking mechanism
     * Note that authentication is currently not supported.
     * @param port the device port to listen on
     * @param username username
     * @param password password
     * @param context context for file access
     */
    public SafDAVServer(int port, String username, String password, Context context) {
        super(hostname, port);
        itemAccess = new DocumentsContractAccess(context);
        this.paths = new ProviderPaths(context);
        respSerializer = new XmlResponseSerialization();
        this.requiredAuthHeader = "Basic " + Base64.encodeToString(
                (username + ':' + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP) ;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if(!checkAuthorization(session)){
            Log.e(TAG, "serve: request not (correctly) authenticated");
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, null, null);
        }
        switch (session.getMethod()) {
            // reading methods
            case GET:
                return onGet(session);
            case PROPFIND:
                return onPropFind(session);
            // writing methods
            case PROPPATCH:
                return onPropPatch(session);
            case MKCOL:
                return onMkCol(session);
            case COPY:
                return onCopy(session);
            case DELETE:
                return onDelete(session);
            case MOVE:
                return onMove(session);
            case PUT:
                return onPut(session);
            // NanoHttpd will serve 501
            default:
                return notImplementedResponse(session, "serve");
        }
    }

    private boolean checkAuthorization(IHTTPSession session) {
        if(null == requiredAuthHeader) {
            return true;
        }
        String suppliedAuthHeader = session.getHeaders().get("authorization");
        return requiredAuthHeader.equals(suppliedAuthHeader);
    }

    private Response badRequest(String signature) {
        Log.d(TAG, signature + ": 400 Bad Request");
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/html", "<!doctype html><html><body><h1>BAD REQEST</h1></body></html>");
    }

    private Response notFound(String signature) {
        Log.d(TAG, signature + ": 404 Not Found");
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
    }

    private Response forbidden(String signature) {
        Log.d(TAG, signature + ": 403 Forbidden");
        return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden");
    }

    private Response notAllowed(String signature) {
        Log.d(TAG, signature + ": 405 Not Allowed");
        return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, null, null);
    }

    private Response conflict(String signature) {
        Log.d(TAG, signature + ": 409 Conflict");
        return newFixedLengthResponse(Response.Status.CONFLICT, null, null);
    }

    private Response preconditionFailed(String signature) {
        Log.d(TAG, signature + ": 412 Precondition Failed");
        return newFixedLengthResponse(Response.Status.PRECONDITION_FAILED, null, null);
    }

    private Response ok(String message, String signature) {
        Log.d(TAG, signature + ": 200 Ok");
        return newFixedLengthResponse(message);
    }

    private Response okXml(String message, String signature) {
        Log.d(TAG, signature + ": 200 Ok");
        return newFixedLengthResponse(Response.Status.OK, "text/xml", message);
    }

    private Response created(String signature) {
        Log.d(TAG, signature + ": 201 Created");
        return newFixedLengthResponse(Response.Status.CREATED, null, null);
    }

    private Response noContent(String signature) {
        Log.d(TAG, signature + ": 204 No Content");
        return newFixedLengthResponse(Response.Status.NO_CONTENT, null, null);
    }

    private Response internalError(String signature) {
        Log.d(TAG, signature + ": 500 Internal Server Error");
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal Error");
    }

    private Response notImplementedResponse(IHTTPSession session, String signature) {
        // Don't dump headers: they may include the local WebDAV Authorization credential.
        Log.e(TAG, signature + ": Request { method " + session.getMethod()
                + ", uri: " + session.getUri() + " }");
        Log.e(TAG, signature + ": not implemented", new Exception("Stack Trace"));
        return null;
    }

    /**
     * Retrieve an entity
     * @param session
     * @return <table>
     *     <tr>
     *         <td>200</td>
     *         <td>OK</td>
     *     </tr>
     *     <tr>
     *         <td>400</td>
     *         <td>Bad Request</td>
     *     </tr>
     * </table>
     */
    protected Response onGet(IHTTPSession session) {
        String requestUri = session.getUri();
        Uri uri = paths.getUriByMappedPath(requestUri);
        SafItem file = null;
        try {
            file = itemAccess.readMeta(uri);
        } catch (ItemNotFoundException e) {
            return notFound("onGet");
        }
        InputStream stream = itemAccess.readFile(file);
        if (null != stream) {
            return newFixedLengthResponse(Response.Status.OK, file.getContentType(), stream, file.getLength());
        }
        return badRequest("onGet");
    }

    /**
     * Retrieve Properties.
     * @param session
     * @return
     */
    protected Response onPropFind(IHTTPSession session) {
        String hDepth = session.getHeaders().get("depth");
        int depth;
        try {
            depth = Integer.parseInt(hDepth);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid depth header, assuming `1´");
            depth = 1;
        }

        // if root, offer permissions; else, normalize (strip permission from) path
        String path = session.getUri();
        Log.d(TAG, "onPropFind: PROPFIND " + path);

        SafItem directory;
        if ("/".equals(path)) {
            directory = paths.getSafRootDirectory();
        } else {
            try {
                Uri uri = paths.getUriByMappedPath(path);
                directory = itemAccess.readMeta(uri);
            } catch (ItemNotFoundException e) {
                return notFound("onPropFind");
            } catch (FileAccessError e) {
                return internalError("onPropFind");
            }
        }
        String requestUri = session.getUri();
        if (!requestUri.contains(SafConstants.SAF_REMOTE_URL)) {
            requestUri = SafConstants.SAF_REMOTE_URL + requestUri.substring(1);
        }

        String responseXml = respSerializer.propFindSerialize(directory, requestUri);
        return okXml(responseXml, "onPropFind");
    }

    /**
     * Change Properties of an item
     * @param session - the incoming HTTP request
     * @return - an appropriate HTTP response
     */
    protected Response onPropPatch(IHTTPSession session) {
        return notImplementedResponse(session, "onPropPatch");
    }

    /**
     * Create a folder
     * @param session the incoming HTTP request
     * @return <table>
     *     <tr>
     *         <td>201</td>
     *         <td>Folder successfully created</td>
     *     </tr>
     *     <tr>
     *         <td>403</td>
     *         <td>Folder is not accessible</td>
     *     </tr>
     *     <tr>
     *         <td>405</td>
     *         <td>Folder already exists</td>
     *     </tr>
     * </table>
     */
    protected Response onMkCol(IHTTPSession session) {
        String path = session.getUri();
        Log.d(TAG, "onMkCol: MKCOL " + path);
        Uri uri;
        try {
            uri = paths.getUriByMappedPath(path);
        } catch (ItemNotFoundException e) {
            return forbidden("onMkCol");
        }
        try {
            itemAccess.createDirectory(uri);
        } catch (ItemNotFoundException e) {
            return notFound("onMkCol");
        } catch (ItemExistsException e) {
            return notAllowed("onMkCol");
        } catch(SecurityException | IllegalStateException e){
            return forbidden("onMkCol");
        }
        return created("onMkCol");
    }

    /**
     * Delete an Entity
     * @param session the incoming HTTP request
     * @return <table>
     *     <tr>
     *         <td>204</td>
     *         <td>File deleted</td>
     *     </tr>
     *     <tr>
     *         <td>403</td>
     *         <td>Folder is not accessible</td>
     *     </tr>
     *     <tr>
     *         <td>404</td>
     *         <td>File not found</td>
     *     </t>
     *     <tr>
     *         <td>501</td>
     *         <td>File Access Error</td>
     *     </tr>
     * </table>
     */
    protected Response onDelete(IHTTPSession session) {
        String path = session.getUri();
        Log.d(TAG, "onDelete: DELETE " + path);
        Uri uri;
        try {
            uri = paths.getUriByMappedPath(path);
        } catch (ItemNotFoundException e) {
            return forbidden("onDelete");
        }
        try {
            itemAccess.deleteItem(uri);
        } catch (FileAccessError e) {
            return internalError("onDelete");
        } catch (ItemNotFoundException e) {
            return notFound("onDelete");
        }
        return noContent("onDelete");
    }

    /**
     * Replace an Entity
     * @param session
     * @return
     */
    protected Response onPut(IHTTPSession session) {
        String path = session.getUri();
        String mime = session.getHeaders().get("content-type");
        String lengthValue = session.getHeaders().get("content-length");
        Log.d(TAG, "onPut: PUT " + path + "; " + mime + "; " + lengthValue + " byte");
        InputStream is = session.getInputStream();
        long length;
        try {
            length = Long.parseLong(lengthValue);
        } catch (NumberFormatException e) {
            return badRequest("onPut");
        }
        if (null == mime) {
            mime = "application/octet-stream";
        }

        Uri uri;
        try {
            uri = paths.getUriByMappedPath(path);
        } catch (ItemNotFoundException e) {
            return forbidden("onPut");
        }

        SafItem item;
        String etag;
        try {
            String name = path.substring(path.lastIndexOf('/') + 1);
            try {
                item = itemAccess.readMeta(uri);
                itemAccess.writeFile(uri, is, length);
            } catch (ItemNotFoundException e) {
                item = itemAccess.createFile(uri, name, mime, is, length);
                etag = "\"" + item.getETag() + "\"";
                Response response = created("onPut");
                response.addHeader("etag", etag);
                return response;
            }
        } catch (SafException e) {
            return internalError("onPut");
        }
        etag = "\"" + item.getETag() + "\"";
        Response response = created("onPut");
        response.addHeader("etag", etag);
        return response;
    }

    /**
     * Copy an Entity. Only works within the server managed space.
     * @param session
     * @return
     */
    protected Response onCopy(IHTTPSession session) {
        return notImplementedResponse(session, "onCopy");
    }

    /**
     * Move an Entity. Depending on the file location, this may be a rename or implemented using copy.
     * @param session
     * @return
     */
    protected Response onMove(IHTTPSession session) {
        String path = session.getUri();
        Uri src = paths.getUriByMappedPath(path);
        boolean noOverwrite = "F".equals(session.getHeaders().get("overwrite"));
        String destination = session.getHeaders().get("destination");
        if (null == destination) {
            return badRequest("onMove");
        }
        Uri dst = paths.getUriByMappedPath(destination.substring(SafConstants.SAF_REMOTE_URL.length() - 1));

        boolean exists = true;
        try {
            itemAccess.readMeta(dst);
        } catch (ItemNotFoundException e) {
            exists = false;
        }
        if (noOverwrite && exists) {
            return preconditionFailed("onMove");
        }

        itemAccess.moveItem(src, dst);

        if (exists) {
            return noContent("onMove");
        } else {
            return created("onMove");
        }
    }

}
