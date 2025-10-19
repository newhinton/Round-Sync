package ca.pkay.rcloneexplorer.Items;

import android.content.Context;

import ca.pkay.rcloneexplorer.R;

/**
 * Copyright (C) 2019  Felix Nüsse
 * Created on 22.12.19 - 14:46
 *
 * Edited by: Felix Nüsse felix.nuesse(at)t-online.de
 *
 * rcloneExplorer
 *
 * This program is released under the MIT license
 *
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class SyncDirectionObject {

    public static final int SYNC_LOCAL_TO_REMOTE = 1;
    public static final int SYNC_REMOTE_TO_LOCAL = 2;
    public static final int COPY_LOCAL_TO_REMOTE = 3;
    public static final int COPY_REMOTE_TO_LOCAL = 4;

    // The first time a bidirectional sync is used, it hast to use --resync. https://rclone.org/bisync/
    public static final int SYNC_BIDIRECTIONAL_INITIAL = 5;
    public static final int SYNC_BIDIRECTIONAL = 6;

    // Move local to remote (use rclone move)
    public static final int MOVE_LOCAL_TO_REMOTE = 7;


    public static String[] getOptionsArray(Context context) {
        return context.getResources().getStringArray(R.array.sync_direction_array);
    }
}
