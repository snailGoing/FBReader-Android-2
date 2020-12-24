package org;


import android.util.Log;

import org.geometerplus.zlibrary.ui.android.BuildConfig;

/**
 * Log output tool class.
 * @author admin
 */
public class LogUtils {
    public final static boolean DEBUG = BuildConfig.DEBUG;

    public static void d(String tag, String msg){
        if (DEBUG) {
            Log.d(tag, msg);
        }
    }

    public static void d(String tag, int msg){
        if (DEBUG) {
            Log.d(tag, "" + msg);
        }
    }


    public static void i(String tag, String msg){
        if (DEBUG) {
            Log.i(tag, msg);
        }
    }

    public static void w(String tag, String msg){
        if (DEBUG) {
            Log.w(tag, msg);
        }
    }
}
