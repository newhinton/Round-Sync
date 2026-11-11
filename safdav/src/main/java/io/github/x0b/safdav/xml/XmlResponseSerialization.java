package io.github.x0b.safdav.xml;

import android.util.Log;
import android.util.TimingLogger;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

import io.github.x0b.safdav.saf.SafFileItem;
import io.github.x0b.safdav.file.SafItem;

/**
 * Serializes folder/file metadata into WebDAV XML files
 */
public class XmlResponseSerialization {

    private static final String TAG = "XmlResponseSerializatn";
    private TimingLogger timings;

    /**
     * Serializes a directory-like document file. Currently only depth=1 is supported.
     *
     * @param item - the {@link SafItem} to serialize
     * @return XML as String
     */
    public String propFindSerialize(SafItem item, String baseUrl, int requestedDepth) {
        timings = new TimingLogger(TAG, "propSerializer");
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><D:multistatus xmlns:D=\"DAV:\">");
        timings.addSplit("onBeforeWriteCollection");
        if(item.isCollection() && !baseUrl.endsWith("/")){
            Log.w(TAG, "Base Url was: " + baseUrl +", appended '/'");
            baseUrl += "/";
        }
        writeResponseElement(b, item, requestedDepth == 0 ? 0 : 1, baseUrl, true);
        timings.addSplit("onAfterWriteCollection");
        b.append("</D:multistatus>");
        timings.dumpToLog();
        return b.toString();
    }

    private void writeResponseElement(StringBuilder sb, SafItem child, int depth, String baseUrl, boolean flattenUrl){
        if(child.isCollection()){
            writeCollection(sb, child, depth, baseUrl, flattenUrl);
        } else {
            writeElement(sb, child, baseUrl, flattenUrl);
        }
    }

    private void writeCollection(StringBuilder sb, SafItem child, int depth, String baseUrl, boolean flattenUrl){
        timings.addSplit("writing collection");
        sb.append("<D:response><D:href>")
                .append(baseUrl)
                .append(flattenUrl ? "" : child.getLink())
                .append("</D:href><D:propstat><D:status>")
                .append(child.getStatus())
                .append("</D:status><D:prop><D:getcontenttype/><D:getlastmodified>")
                .append(child.getLastModified())
                .append("</D:getlastmodified><D:lockdiscovery/><D:ishidden>0</D:ishidden>")
                // Add Lock states if supportable
                .append("<D:getetag/><D:displayname>")
                .append(xmlEscape(child.getName()))
                .append("</D:displayname><D:getcontentlanguage/><D:getcontentlength>")
                .append(child.getContentLength())
                .append("</D:getcontentlength><D:iscollection>1</D:iscollection><D:creationdate>")
                .append(child.getCreationDate())
                .append("</D:creationdate><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat></D:response>");
        if(depth >= 1 && child.isCollection()){
            timings.addSplit("writing child");
            for(SafItem grandChild : child.getChildren()){
                writeResponseElement(sb, grandChild, depth-1, baseUrl, false);
            }
        }
    }

    private void writeElement(StringBuilder sb, SafItem child, String baseUrl, boolean flattenUrl){
        timings.addSplit("start:child");
        sb.append("<D:response><D:href>")
                .append(baseUrl)
                .append(flattenUrl ? "" : timed(child.getLink()))
                .append("</D:href><D:propstat><D:status>")
                .append(timed(child.getStatus()))
                .append("</D:status><D:prop><D:getcontenttype>")
                .append(timed(child.getContentType()))
                .append("</D:getcontenttype><D:getlastmodified>")
                .append(timed(child.getLastModified()))
                .append("</D:getlastmodified><D:lockdiscovery/><D:ishidden>0</D:ishidden><D:getetag>")
                .append(timed(child.getETag()))
                .append("</D:getetag><D:displayname>")
                .append(timed(xmlEscape(child.getName())))
                .append("</D:displayname><D:getcontentlanguage/><D:getcontentlength>")
                .append(timed(child.getContentLength()))
                .append("</D:getcontentlength><D:iscollection>0</D:iscollection><D:creationdate>")
                .append(timed(child.getCreationDate()))
                .append("</D:creationdate><D:resourcetype/></D:prop></D:propstat></D:response>");
        timings.addSplit("end:child");
    }

    private String xmlEscape(String input){
        if(input == null)
            return "";
        char[] problems = {'"', '\'', '<', '>', '&'};
        String[] escapeEntities = {"&quot;", "&apos;", "&lt;", "&gt;", "&amp;"};
        char[] inputStr = input.toCharArray();
        StringBuilder escaped = new StringBuilder(input.length());
        character:
        for(int j = 0; j < input.length(); j++){
            for(int i = 0; i < problems.length; i++){
                if(inputStr[j] == problems[i]){
                    escaped.append(escapeEntities[i]);
                    continue character;
                }
            }
            escaped.append(inputStr[j]);
        }
        return escaped.toString();
    }

    private String timed(String string){
        //timings.addSplit("timed call");
        return string;
    }

    /**
     * Writes a SafFileItem into a xml serializer. Note that depth is not actually
     * infinite, but limited to the available space for the serializer and may
     * crash when attempting to parse very large trees at once.
     * @param serializer a @{@link XmlSerializer} to write to
     * @param child a safitem that is written as xml response
     * @param depth the depth of the folder structure. Unless otherwise needed,
     *              use 1 which will print the item and - for collections - the
     *              item's children, but not their children. Note that for
     *              values <1 the item will be written, but no children.
     * @param flattenChildUrl true will overwrite the current url with "/"
     */
    private void writeResponse(XmlSerializer serializer, SafFileItem child, int depth, boolean flattenChildUrl) throws IOException {
        serializer.startTag("DAV:", "response")
           .startTag("DAV:", "href")
              .text(flattenChildUrl ? "/" : child.getLink())
           .endTag("DAV:", "href")

           .startTag("DAV:", "propstat")
              .startTag("DAV:", "prop")
                 .startTag("DAV:", "creationdate")
                   .text(child.getCreationDate())                                    // creationDate not supported by DocumentFile
                 .endTag("DAV:", "creationdate");

        if(child.isCollection()) {
            serializer.startTag("DAV:", "resourcetype")
                        .startTag("DAV:", "collection")
                        .endTag("DAV:", "collection")
                    .endTag("DAV:", "resourcetype")

                    .startTag("DAV:", "iscollection")
                        .text("1")
                    .endTag("DAV:", "iscollection")

                    .startTag("DAV:", "getetag")
                    .endTag("DAV:", "getetag");
        } else {
            serializer.startTag("DAV:", "getetag")
                    .text(child.getETag())
                    .endTag("DAV:", "getetag")

                    .startTag("DAV:", "getcontenttype")
                    .text(child.getContentType())
                    .endTag("DAV:", "getcontenttype");
        }
        serializer.startTag("DAV:", "getlastmodified")
                .text(child.getLastModified())
                .endTag("DAV:", "getlastmodified")



                .startTag("DAV:", "getcontentlength")
                .text(child.getContentLength())
                .endTag("DAV:", "getcontentlength")

              .endTag("DAV:", "prop")
              .startTag("DAV:", "status")
                .text(child.getStatus())
              .endTag("DAV:", "status")
           .endTag("DAV:", "propstat")
        .endTag("DAV:", "response");

        // writes recursively the tree as xml props
        if(depth >= 1 && child.isCollection()){
            for(SafFileItem grandChild : child.getChildren()){
                writeResponse(serializer, grandChild, depth-1, false);
            }
        }
    }
}
