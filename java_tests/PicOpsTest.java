package com.drscbt.shared.piclib;

import com.drscbt.shared.assetloader.ApkJarAssetLoaderProv;
import com.drscbt.shared.assetloader.IApkJarAssetLoader;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class PicOpsTest {
    @Test
    public void copy() {
        PicData src = this._loadPicResource("micro_canvas.ppm");
        PicData exp = this._loadPicResource("micro_pat.ppm");
        PicData copy = PicData.create(6, 3);
        PicOps.copy(src, copy, 3, 2);
        assertArrayEquals(exp.rgba, copy.rgba);
    }

    @Test
    public void rotateCopy() {
        PicData src = this._loadPicResource("split-vert-1.ppm");
        PicData exp = this._loadPicResource("split-hor-1.ppm");
        PicData copy = PicOps.copyCCW90(src);
        assertArrayEquals(exp.rgba, copy.rgba);
    }

    @Test
    public void replace() {
        PicData src = this._loadPicResource("replace-input.png");
        PicData exp = this._loadPicResource("replace-result.png");
        PicData dst = src.copy();
        PicOps.replace(dst, 0, 255, 1, 255, 0, 255, 0xFFFFFFFF);
        assertArrayEquals(exp.rgba, dst.rgba);
    }

    private PicData _loadPicResource(String name) {
        String picName = String.format("match_test_pics/%s", name);
        IApkJarAssetLoader ldr = ApkJarAssetLoaderProv.getLoader();
        return PlatformImgToolsExtProv.getTools().fromExt(ldr.load(picName), name);
    }
}
