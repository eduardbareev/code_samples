package com.drscbt.shared.color;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.drscbt.shared.assetloader.ApkJarAssetLoaderProv;
import com.drscbt.shared.assetloader.IApkJarAssetLoader;
import com.drscbt.shared.piclib.ImgTools;
import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclib.PicGrayscale;
import com.drscbt.shared.piclib.PlatformImgToolsExtProv;
import com.drscbt.shared.utils.Utils;
import org.junit.Test;

public class ColorConvTest {
    private ImgTools _imgTools;

    static final private int[][] _mapRgbToHsl255 = new int[][]{
        {  0,   0,   0,     0,   0,   0},
        {255, 255, 255,     0,   0, 255},
        {127,  99,  99,     0,  56, 127},
        { 50, 200,  50,    85, 191, 200},
        { 20,  20,  50,   170, 153,  50},
        { 54, 179,  64,    88, 178, 179},
    };

    @Test
    public void hsv255ToRgbTbl(){
        for (int[] p : _mapRgbToHsl255) {
            ColorConv.HsvC255 hsvIn = new ColorConv.HsvC255(p[3],p[4],p[5]);
            ColorConv.RgbC rgbOutExp = new ColorConv.RgbC(p[0],p[1],p[2]);
            ColorConv.RgbC rgbOutActual = ColorConv.hsv255ToRgb(hsvIn);
            this._perComponentCompare(rgbOutExp, rgbOutActual, 1);
        }
    }

    @Test
    public void rgbToHsv255Tbl() {
        for (int[] p : _mapRgbToHsl255) {
            ColorConv.RgbC rgbIn = new ColorConv.RgbC(p[0],p[1],p[2]);
            ColorConv.HsvC255 hsvOutExp = new ColorConv.HsvC255(p[3],p[4],p[5]);
            ColorConv.HsvC255 hsvOutActual = new ColorConv.HsvC255();

            ColorConv.rgbPxToHsv255J(rgbIn, hsvOutActual);
            assertEquals(hsvOutExp, hsvOutActual);

            ColorConv.rgbPxToHsv255Na(rgbIn, hsvOutActual);
            assertEquals(hsvOutExp, hsvOutActual);
        }
    }

    @Test
    public void equalsTest() {
        ColorConv.RgbC rgb1 = new ColorConv.RgbC(1,2,3);
        ColorConv.RgbC rgb2 = new ColorConv.RgbC(1,2,3);
        assertEquals(rgb1, rgb2);
    }

    @Test
    public void toStrTest() {
        assertEquals("rgb(1, 2, 3)",new ColorConv.RgbC(1,2,3).toString());
        assertEquals("hsv(1, 2, 3)",new ColorConv.HsvC360_100(1,2,3).toString());
    }

    @Test
    public void rgbToHsv255NaArrayTbl() {
        int[] tblRgb = new int[_mapRgbToHsl255.length];
        int[] tblHsvExp = new int[_mapRgbToHsl255.length];
        int[] tblHsvAct = new int[_mapRgbToHsl255.length];

        for (int i = 0; i < _mapRgbToHsl255.length; i++) {
            int[] p = _mapRgbToHsl255[i];
            tblRgb[i] = Utils.fourIntsToInt(p[0],p[1],p[2], 0xFF);
            tblHsvExp[i] = Utils.fourIntsToInt(p[3],p[4],p[5], 0xFF);
        }

        ColorConv.rgbArrToHsv255Na(tblRgb, tblHsvAct);
        assertArrayEquals(tblHsvExp ,tblHsvAct);
    }

    @Test
    public void grayscaleRegionCopy() {
        PicData src = this._loadResPic("micro_canvas.ppm");
        PicGrayscale copyExp = this._loadResPicGray("micro_pat_gray.pgm");
        PicGrayscale copy = PicGrayscale.create(6, 3);
        ColorConv.grayscale(src, copy, 3, 2);
        assertArrayEquals(copyExp.data, copy.data);
    }

    @Test
    public void grayscale() {
        PicData src = this._loadResPic("micro_pat.ppm");
        PicGrayscale exp = this._loadResPicGray("micro_pat_gray.pgm");
        PicGrayscale act = PicGrayscale.create(exp.width, exp.height);

        ColorConv.grayscale(src, act);
        assertArrayEquals(exp.data, act.data);
    }

    private PicData _loadResPic(String name) {
        String picName = String.format("match_test_pics/%s", name);
        IApkJarAssetLoader ldr = ApkJarAssetLoaderProv.getLoader();
        return PlatformImgToolsExtProv.getTools().fromExt(ldr.load(picName), picName);
    }

    private PicGrayscale _loadResPicGray(String name) {
        String picName = String.format("match_test_pics/%s", name);
        IApkJarAssetLoader ldr = ApkJarAssetLoaderProv.getLoader();
        return PlatformImgToolsExtProv.getTools().fromExtGray(ldr.load(picName), picName);
    }

    private void _perComponentCompare(ColorConv.ThreeCompIntColor a, ColorConv.ThreeCompIntColor b, int err) {
        if (a.equals(b)) {
            return;
        }

        if ((Math.abs(a.c1 - b.c1) > err)
            || (Math.abs(a.c2 - b.c2) > err)
            || (Math.abs(a.c3 - b.c3) > err)) {
            throw new AssertionError(String.format("%s is too far from %s", a, b));
        }
    }

    public ColorConvTest() {
        this._imgTools = PlatformImgToolsExtProv.getTools();
    }
}
