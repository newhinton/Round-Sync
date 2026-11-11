package ca.pkay.rcloneexplorer;

import java.io.File;
import java.util.Comparator;
import java.util.Locale;

import ca.pkay.rcloneexplorer.Items.FileItem;

public class FileComparators {

        public static class SortAlphaDescending implements Comparator<FileItem> {

            @Override
            public int compare(FileItem fileItem, FileItem t1) {
                if (fileItem.isDir() && !t1.isDir()) {
                    return -1;
                } else if (!fileItem.isDir() && t1.isDir()) {
                    return 1;
                }

                return t1.getName().toLowerCase(Locale.ROOT).compareTo(fileItem.getName().toLowerCase(Locale.ROOT));
            }
        }

        public static class SortAlphaAscending implements Comparator<FileItem> {

            @Override
            public int compare(FileItem fileItem, FileItem t1) {
                if (fileItem.isDir() && !t1.isDir()) {
                    return -1;
                } else if (!fileItem.isDir() && t1.isDir()) {
                    return 1;
                }

                return fileItem.getName().toLowerCase(Locale.ROOT).compareTo(t1.getName().toLowerCase(Locale.ROOT));
            }
        }

        public static class SortSizeDescending implements Comparator<FileItem> {

            @Override
            public int compare(FileItem fileItem, FileItem t1) {
                if (fileItem.isDir() && !t1.isDir()) {
                    return -1;
                } else if (!fileItem.isDir() && t1.isDir()) {
                    return 1;
                }

                if (fileItem.isDir() && t1.isDir()) {
                    return fileItem.getName().compareTo(t1.getName());
                }

                return Long.compare(t1.getSize(), fileItem.getSize());
            }
        }

        public static class SortSizeAscending implements Comparator<FileItem> {

            @Override
            public int compare(FileItem fileItem, FileItem t1) {
                if (fileItem.isDir() && !t1.isDir()) {
                    return -1;
                } else if (!fileItem.isDir() && t1.isDir()) {
                    return 1;
                }

                if (fileItem.isDir() && t1.isDir()) {
                    return fileItem.getName().compareTo(t1.getName());
                }

                return Long.compare(fileItem.getSize(), t1.getSize());
            }
        }

        public static class SortModTimeDescending implements Comparator<FileItem> {

            @Override
            public int compare(FileItem fileItem, FileItem t1) {
                if (fileItem.isDir() && !t1.isDir()) {
                    return -1;
                } else if (!fileItem.isDir() && t1.isDir()) {
                    return 1;
                }

                return Long.compare(t1.getModTime(), fileItem.getModTime());
            }
        }

        public static class SortModTimeAscending implements Comparator<FileItem> {

            @Override
            public int compare(FileItem fileItem, FileItem t1) {
                if (fileItem.isDir() && !t1.isDir()) {
                    return -1;
                } else if (!fileItem.isDir() && t1.isDir()) {
                    return 1;
                }

                return Long.compare(fileItem.getModTime(), t1.getModTime());
            }
        }


    public static class SortFileAlphaDescending implements Comparator<File> {

        @Override
        public int compare(File file, File t1) {
            if (file.isDirectory() && !t1.isDirectory()) {
                return -1;
            } else if (!file.isDirectory() && t1.isDirectory()) {
                return 1;
            }

            return t1.getName().toLowerCase(Locale.ROOT).compareTo(file.getName().toLowerCase(Locale.ROOT));
        }
    }

    public static class SortFileAlphaAscending implements Comparator<File> {

        @Override
        public int compare(File o1, File o2) {
            if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            }

            return o1.getName().toLowerCase(Locale.ROOT).compareTo(o2.getName().toLowerCase(Locale.ROOT));
        }
    }

    public static class SortFileSizeDescending implements Comparator<File> {

        @Override
        public int compare(File o1, File o2) {
            if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            }

            if (o1.isDirectory() && o2.isDirectory()) {
                return o1.getName().toLowerCase(Locale.ROOT).compareTo(o2.getName().toLowerCase(Locale.ROOT));
            }

            return Long.compare(o2.length(), o1.length());
        }
    }

    public static class SortFileSizeAscending implements Comparator<File> {

        @Override
        public int compare(File o1, File o2) {
            if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            }

            if (o1.isDirectory() && o2.isDirectory()) {
                return o1.getName().toLowerCase(Locale.ROOT).compareTo(o2.getName().toLowerCase(Locale.ROOT));
            }

            return Long.compare(o1.length(), o2.length());
        }
    }

    public static class SortFileModTimeDescending implements Comparator<File> {

        @Override
        public int compare(File o1, File o2) {
            if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            }

            return Long.compare(o2.lastModified(), o1.lastModified());
        }
    }

    public static class SortFileModTimeAscending implements Comparator<File> {

        @Override
        public int compare(File o1, File o2) {
            if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            }

            return Long.compare(o1.lastModified(), o2.lastModified());
        }
    }
}
