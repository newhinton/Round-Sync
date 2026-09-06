package ca.pkay.rcloneexplorer.Items;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import ca.pkay.rcloneexplorer.R;
import io.github.x0b.safdav.file.SafConstants;

public class RemoteItem implements Comparable<RemoteItem>, Parcelable {

    /**
     * A remote of type SAFW, a virtual WebDAV remote made backed by Storage
     * Access Framework (SAF).
     */
    public static final int SAFW = -10;

    // All recognized remote types. Must correspond to the numbering when
    // running rclone config (for remotes that can only be configured
    // interactively).
    // Updated to: rclone 1.50.2
    public static final int FICHIER = 1;
    public static final int ALIAS = 2;
    public static final int AMAZON_DRIVE = 3;
    public static final int S3 = 4;
    public static final int B2 = 5;
    public static final int BOX = 6;
    public static final int CACHE = 7;
    public static final int SHAREFILE = 8;
    public static final int DROPBOX = 9;
    public static final int CRYPT = 10;
    public static final int FTP = 11;
    public static final int GOOGLE_CLOUD_STORAGE = 12;
    public static final int GOOGLE_DRIVE = 13;
    public static final int GOOGLE_PHOTOS = 14;
    public static final int JOTTACLOUD = 16;
    public static final int KOOFR = 17;
    public static final int LOCAL = 18;
    public static final int MAILRU = 19;
    public static final int MEGA = 20;
    public static final int AZUREBLOB = 21;
    public static final int ONEDRIVE = 22;
    public static final int OPENDRIVE = 23;
    public static final int SWIFT = 24;
    public static final int PCLOUD = 25;
    public static final int PUTIO = 26;
    public static final int QINGSTOR = 27;
    public static final int SFTP = 28;
    public static final int CHUNKER = 29;
    public static final int UNION = 30;
    public static final int WEBDAV = 31;
    public static final int YANDEX = 32;
    public static final int HTTP = 33;
    public static final int PREMIUMIZEME = 34;
    public static final int INTERNXT = 35;
    public static final int DRIME = 36;

    private String name;
    @Deprecated
    private int type;
    private String typeReadable;
    private boolean isCrypt;
    private boolean isAlias;
    private boolean isPathAlias;
    private boolean isCache;
    private boolean isPinned;
    private boolean isDrawerPinned;
    private String displayName;

    public RemoteItem(String name, String type) {
        this.name = name;
        this.typeReadable = type;
        this.type = getTypeFromString(type);
    }

    @Deprecated
    public RemoteItem(String name, int type, String typeReadable) {
        this.name = name;
        this.typeReadable = typeReadable;
        this.type = type;
    }

    private RemoteItem(Parcel in) {
        name = in.readString();
        displayName = in.readString();
        type = in.readInt();
        typeReadable = in.readString();
        isCrypt = in.readByte() != 0;
        isAlias = in.readByte() != 0;
        isCache = in.readByte() != 0;
        isPinned = in.readByte() != 0;
        isDrawerPinned = in.readByte() != 0;
        isPathAlias = in.readByte() != 0;
    }

    public static final Creator<RemoteItem> CREATOR = new Creator<RemoteItem>() {
        @Override
        public RemoteItem createFromParcel(Parcel in) {
            return new RemoteItem(in);
        }

        @Override
        public RemoteItem[] newArray(int size) {
            return new RemoteItem[size];
        }
    };

    public boolean hasTrashCan() {
        switch (type) {
            case GOOGLE_DRIVE:
            case PCLOUD:
            case YANDEX:
                return true;
            default:
                return false;
        }
    }

    public boolean isDirectoryModifiedTimeSupported() {
        switch (type) {
            case DROPBOX:
            case B2:
            case GOOGLE_PHOTOS:
                return false;
            default:
                return true;
        }
    }

    public boolean isOAuth() {
        switch (type) {
            case PCLOUD:
            case PREMIUMIZEME:
            case BOX:
            case PUTIO:
            case SHAREFILE:
            case ONEDRIVE:
            case YANDEX:
            case AMAZON_DRIVE:
            case GOOGLE_PHOTOS:
            case GOOGLE_DRIVE:
            case GOOGLE_CLOUD_STORAGE:
            case DROPBOX:
            case JOTTACLOUD:
            case MAILRU:
                return true;
            default:
                return false;
        }
    }

    public boolean hasLinkSupport(){
        if (isRemoteType(CRYPT, LOCAL, SAFW) || isPathAlias) {
            return false;
        }
        return true;
    }

    // TODO: check callers once destination sync is added
    public boolean hasSyncSupport() {
        if (isRemoteType(LOCAL, SAFW) || isPathAlias) {
            return false;
        }
        return true;
    }

    public String getName() {
        return name;
    }

    @Deprecated
    public int getType() {
        return type;
    }

    public void setType(String type) {
        this.typeReadable = type;
        this.type = getTypeFromString(type);
    }

    public String getTypeReadable() {
        return this.typeReadable;
    }

    public boolean isCrypt() {
        return this.isCrypt;
    }

    public RemoteItem setIsCrypt(boolean isCrypt) {
        this.isCrypt = isCrypt;
        return this;
    }

    public boolean isAlias() {
        return this.isAlias;
    }

    public RemoteItem setIsAlias(boolean isAlias) {
        this.isAlias = isAlias;
        return this;
    }

    /**
     * If the remote is an alias to a local path like /storage/c0ffe.
     */
    public boolean isPathAlias() {
        return isPathAlias;
    }

    public void setIsPathAlias(boolean pathAlias) {
        isPathAlias = pathAlias;
    }

    public boolean isCache() {
        return this.isCache;
    }

    public RemoteItem setIsCache(boolean isCache) {
        this.isCache = isCache;
        return this;
    }

    public RemoteItem pin(boolean isPinned) {
        this.isPinned = isPinned;
        return this;
    }

    public boolean isPinned() {
        return this.isPinned;
    }

    public RemoteItem setDrawerPinned(boolean isPinned) {
        this.isDrawerPinned = isPinned;
        return this;
    }

    public boolean isDrawerPinned() {
        return this.isDrawerPinned;
    }

    @Deprecated
    public boolean isRemoteType(int ...remotes) {
        boolean isSameType = false;

        for (int remote : remotes) {
            if (this.type == remote) {
                isSameType = true;
            }
        }
        return isSameType;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public static List<RemoteItem> prepareDisplay(Context context, List<RemoteItem> items) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> renamedRemotes = pref.getStringSet(context.getString(R.string.pref_key_renamed_remotes), new HashSet<>());
        for(RemoteItem item : items) {
            if(renamedRemotes.contains(item.name)) {
                String displayName = pref.getString(
                        context.getString(R.string.pref_key_renamed_remote_prefix, item.name), item.name);
                item.displayName = displayName;
            }
        }
        return items;
    }

    @Deprecated
    private int getTypeFromString(String type) {
        switch (type) {
            case SafConstants.SAF_REMOTE_NAME:
                return SAFW;
            case "alias":
                return ALIAS;
            case "amazon cloud drive":
                return AMAZON_DRIVE;
            case "azureblob":
                return AZUREBLOB;
            case "b2":
                return B2;
            case "box":
                return BOX;
            case "cache":
                return CACHE;
            case "chunker":
                return CHUNKER;
            case "crypt":
                return CRYPT;
            case "dropbox":
                return DROPBOX;
            case "drive":
                return GOOGLE_DRIVE;
            case "fichier":
                return FICHIER;
            case "ftp":
                return FTP;
            case "google cloud storage":
                return GOOGLE_CLOUD_STORAGE;
            case "google photos":
                return GOOGLE_PHOTOS;
            case "http":
                return HTTP;
            case "swift":
                return SWIFT;
            case "jottacloud":
                return JOTTACLOUD;
            case "koofr":
                return KOOFR;
            case "local":
                return LOCAL;
            case "mega":
                return MEGA;
            case "onedrive":
                return ONEDRIVE;
            case "opendrive":
                return OPENDRIVE;
            case "pcloud":
                return PCLOUD;
            case "qingstor":
                return QINGSTOR;
            case "s3":
                return S3;
            case "sftp":
                return SFTP;
            case "union":
                return UNION;
            case "webdav":
                return WEBDAV;
            case "yandex":
                return YANDEX;
            case "sharefile":
                return SHAREFILE;
            case "mailru":
                return MAILRU;
            case "putio":
                return PUTIO;
            case "premiumizeme":
                return PREMIUMIZEME;
            case "internxt":
                return INTERNXT;
            case "drime":
                return DRIME;
            default:
                return -1;
        }
    }

    public int getRemoteIcon() {
        return getRemoteIcon(type);
    }

    public int getRemoteIcon(int type) {
        if (isCrypt()) {
            return R.drawable.ic_lock_black;
        } else {
            switch (type) {
                case RemoteItem.SAFW:
                    return R.drawable.ic_tablet_cellphone;
                case RemoteItem.AMAZON_DRIVE:
                    return R.drawable.ic_amazon;
                case RemoteItem.AZUREBLOB:
                    return R.drawable.ic_azure_storage_blob_logo;
                case RemoteItem.B2:
                    return R.drawable.ic_backblaze_b2_black;
                case RemoteItem.GOOGLE_DRIVE:
                    return R.drawable.ic_google_drive;
                case RemoteItem.DROPBOX:
                    return R.drawable.ic_dropbox;
                case RemoteItem.GOOGLE_CLOUD_STORAGE:
                    return R.drawable.ic_google;
                case RemoteItem.GOOGLE_PHOTOS:
                    return R.drawable.ic_google_photos;
                case RemoteItem.KOOFR:
                    return R.drawable.ic_koofr;
                case RemoteItem.MEGA:
                    return R.drawable.ic_mega_logo_black;
                case RemoteItem.ONEDRIVE:
                    return R.drawable.ic_onedrive;
                case RemoteItem.OPENDRIVE:
                    return R.drawable.ic_open_drive;
                case RemoteItem.S3:
                    return R.drawable.ic_amazon;
                case RemoteItem.BOX:
                    return R.drawable.ic_box;
                case RemoteItem.SFTP:
                    return R.drawable.ic_terminal;
                case RemoteItem.LOCAL:
                    return R.drawable.ic_tablet_cellphone;
                case RemoteItem.PCLOUD:
                    return R.drawable.ic_pcloud;
                case RemoteItem.UNION:
                    return R.drawable.ic_union_24dp;
                case RemoteItem.WEBDAV:
                    return R.drawable.ic_webdav;
                case RemoteItem.YANDEX:
                    return R.drawable.ic_yandex_mono;
                default:
                    return R.drawable.ic_cloud;
            }
        }
    }

    @Override
    public int compareTo(@NonNull RemoteItem remoteItem) {
        if (this.isPinned && !remoteItem.isPinned()) {
            return -1;
        } else if (!this.isPinned && remoteItem.isPinned) {
            return 1;
        }
        return getDisplayName().toLowerCase(Locale.ROOT).compareTo(remoteItem.getDisplayName().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RemoteItem && ((RemoteItem) obj).getName().equals(this.name) && ((RemoteItem) obj).getType() == this.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(displayName);
        dest.writeInt(type);
        dest.writeString(typeReadable);
        dest.writeByte((byte) (isCrypt ? 1 : 0));
        dest.writeByte((byte) (isAlias ? 1 : 0));
        dest.writeByte((byte) (isCache ? 1 : 0));
        dest.writeByte((byte) (isPinned ? 1 : 0));
        dest.writeByte((byte) (isDrawerPinned ? 1 : 0));
        dest.writeByte((byte) (isPathAlias ? 1 : 0));
    }
}
