package ca.pkay.rcloneexplorer.Items;

import android.content.Context;

import ca.pkay.rcloneexplorer.R;

public class FilterEntry {

    public static final int FILTER_INCLUDE = 0;
    public static final int FILTER_EXCLUDE = 1;

    public int filterType = FILTER_EXCLUDE;

    public String filter;


    public FilterEntry(int filterType, String filter) {
        this.filterType = filterType;
        this.filter = filter;
    }
}
