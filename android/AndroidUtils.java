package com.drscbt.andrapp.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.storage.StorageManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.drscbt.andrapp.Application;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AndroidUtils {
    static private File _remPath;

    static public File getRemovablePath() {
        if (AndroidUtils._remPath == null) {
            AndroidUtils._remPath = AndroidUtils._getRemovablePath();
        }
        return AndroidUtils._remPath;
    }

    static private File _getRemovablePath() {
        StorageManager storageManager
            = (StorageManager) Application.getAppContext()
                .getSystemService(
                    Application.getAppContext().STORAGE_SERVICE
                );

        Class<?> storageVolumeCls;
        try {
            storageVolumeCls = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeListMehod = storageManager.getClass().getMethod("getVolumeList");
            Method getPathMethod = storageVolumeCls.getMethod("getPath");
            Method isRemovableMethod = storageVolumeCls.getMethod("isRemovable");
            Object result = getVolumeListMehod.invoke(storageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVol = Array.get(result, i);
                String path = (String) getPathMethod.invoke(storageVol);
                if ((Boolean) isRemovableMethod.invoke(storageVol)) {
                    return new File(path);
                }
            }
            return null;
        } catch (IllegalAccessException|ClassNotFoundException
                |InvocationTargetException |NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static DisplayMetrics getDisplayMetrics() {
        return Application.getApplication().getResources().getDisplayMetrics();
    }

    public static Configuration getConResConf() {
        return Application.getApplication().getResources().getConfiguration();
    }

    public static DispSize getRealDispSize() {
        WindowManager wm
            = (WindowManager) Application
                .getAppContext().getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        return new DispSize(p.x, p.y);
    }

    public static DispSize getDispSize() {
        DisplayMetrics displayMetrics = AndroidUtils.getDisplayMetrics();
        return new DispSize(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    public static int getDispDensity() {
        return AndroidUtils.getDisplayMetrics().densityDpi;
    }

    public static boolean hasSoftwareKeys() {
        DispSize ds1 = getDispSize();
        DispSize ds2 = getRealDispSize();
        return ds2.h > ds1.h;
    }

    public static class DispSize {
        public int w;
        public int h;

        public DispSize(int w, int h) {
            this.w = w;
            this.h = h;
        }

        public String toString() {
            return String.format("DispSize{%dx%d}", this.w, this.h);
        }
    }

    public static String getProp(String prop) {
        String line;
        BufferedReader buffRdr = null;
        try {
            Process p = Runtime.getRuntime().exec("getprop " + prop);
            buffRdr = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = buffRdr.readLine();
            buffRdr.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if(buffRdr != null) {
                try {
                    buffRdr.close();
                } catch (IOException e) {
                    // swallow
                }
            }
        }
        return line;
    }

    public static void closeActNExit(Activity activity) {
        new ScheduledThreadPoolExecutor(1).schedule(
                () -> android.os.Process.killProcess(android.os.Process.myPid()),
                300, TimeUnit.MILLISECONDS);
        activity.finishAndRemoveTask();
    }

    public static boolean serviceRunning(Class serviceClass){
        ActivityManager am
            = (ActivityManager)Application.getAppContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services
            = am.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo s : services) {
            if (s.service.getClassName().equals(serviceClass.getName())){
                return true;
            }
        }
        return false;
    }

    public static String versionName() {
        PackageManager pm = Application.getApplication().getPackageManager();
        String pname = Application.getApplication().getPackageName();
        PackageInfo info;
        try {
            info = pm.getPackageInfo(pname, PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);

        }
        return info.versionName;
    }

    public static int versionCode() {
        PackageManager pm = Application.getApplication().getPackageManager();
        String pname = Application.getApplication().getPackageName();
        PackageInfo info;
        try {
            info = pm.getPackageInfo(pname, PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);

        }
        return info.versionCode;
    }
}
