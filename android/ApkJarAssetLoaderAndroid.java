package com.drscbt.platf_a;

import android.content.Context;
import android.content.res.AssetManager;
import android.support.test.InstrumentationRegistry;

import com.drscbt.andrapp.Application;
import com.drscbt.shared.assetloader.IApkJarAssetLoader;
import com.drscbt.shared.utils.UncheckedFileNotFoundException;
import com.drscbt.shared.utils.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class ApkJarAssetLoaderAndroid implements IApkJarAssetLoader {
    private Context _ctx;
    private AssetManager _assetsMngr;

    public ApkJarAssetLoaderAndroid(Context ctx) {
        this._ctx = ctx;
        this._assetsMngr = this._ctx.getAssets();
    }

    public ApkJarAssetLoaderAndroid() {
        this._ctx = this._getContext();
        this._assetsMngr = this._ctx.getAssets();
    }

    public List<String> list(String dirPath) {
        dirPath = this._dirPathToList(dirPath);
        List<String> lst;
        try {
            String[] lstA = this._assetsMngr.list(dirPath);
            lst = Arrays.asList(lstA);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return lst;
    }

    private String _dirPathToList(String dirPath) {
        dirPath = dirPath.replaceAll("/+$", "");
        dirPath = dirPath.replaceAll("^/+", "");
        return dirPath;
    }

    private String _filePathToLoad(String filePath) {
        return filePath.replaceAll("^/+", "");
    }

    public InputStream load(String filePath) {
        filePath = this._filePathToLoad(filePath);
        try {
            return this._assetsMngr.open(filePath);
        } catch (IOException e) {
            throw new UncheckedFileNotFoundException(e);
        }
    }

    public int size(String filePath) {
        filePath = this._filePathToLoad(filePath);
        try {
            return (int) this._assetsMngr.openFd(filePath).getLength();
        } catch (FileNotFoundException e) {
            InputStream is = null;
            try {
                is = this._assetsMngr.open(filePath);
            } catch (FileNotFoundException ex) {
                // file doesn't exist
                throw new UncheckedFileNotFoundException(e);
            } catch (IOException ex) {
                throw new RuntimeException(e);
            }
            // false FileNotFoundException. openFd() failure
            int s = Utils.readToNull(is);
            try {
                is.close();
            } catch (IOException ex) {
                //
            }
            return s;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean exists(String filePath) {
        throw new UnsupportedOperationException("not implemeted");
    }

    private Context _getContext() {
        if (this.isInstrumentationTest()) {
            return InstrumentationRegistry.getInstrumentation().getContext();
        } else {
            return Application.getAppContext();
        }
    }

    private boolean isInstrumentationTest() {
        boolean instTest = true;
        try {
            InstrumentationRegistry.getInstrumentation();
        } catch (IllegalStateException e) {
            instTest = false;
        }
        return instTest;
    }
}
