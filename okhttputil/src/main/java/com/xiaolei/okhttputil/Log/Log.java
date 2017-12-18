package com.xiaolei.okhttputil.Log;


import com.xiaolei.okhttputil.OkHttpUtilConfig;

/**
 * Created by xiaolei on 2017/3/9.
 */

public class Log
{
    public static void i(String tag, Object object)
    {
        if (OkHttpUtilConfig.DEBUG) android.util.Log.i(tag, "" + object);
    }

    public static void e(String tag, Object object)
    {
        if (OkHttpUtilConfig.DEBUG) android.util.Log.e(tag, "" + object);
    }

    public static void v(String tag, Object object)
    {
        if (OkHttpUtilConfig.DEBUG) android.util.Log.v(tag, "" + object);
    }

    public static void w(String tag, Object object)
    {
        if (OkHttpUtilConfig.DEBUG) android.util.Log.w(tag, "" + object);
    }

    public static void d(String tag, Object object)
    {
        if (OkHttpUtilConfig.DEBUG) android.util.Log.d(tag, "" + object);
    }
}
