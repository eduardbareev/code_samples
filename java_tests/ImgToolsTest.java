package com.drscbt.shared.piclib;

import com.drscbt.shared.assetloader.ApkJarAssetLoaderProv;
import com.drscbt.shared.assetloader.IApkJarAssetLoader;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class ImgToolsTest {
    private ImgTools _imgTools;

    @Test
    public void pngDecode() {
        PicData picDataFromPng = this._imgTools.fromPNG(this._loadTestResource("micro_canvas_o25_d12.png"));
        PicData picDataFromPpmP6 = this._imgTools.fromPPM(this._loadTestResource("micro_canvas_o25_d12.p6.ppm"));
        assertArrayEquals(picDataFromPpmP6.rgba, picDataFromPng.rgba);
    }

    @Test
    public void ppm3And6Read() {
        PicData p6 = this._imgTools.fromPPM(this._loadTestResource("micro_canvas_o25_d12.p6.ppm"));
        PicData p3 = this._imgTools.fromPPM(this._loadTestResource("micro_canvas_o25_d12.p3.ppm"));
        PicData png = this._imgTools.fromPNG(this._loadTestResource("micro_canvas_o25_d12.png"));
        assertArrayEquals(png.rgba, p6.rgba);
        assertArrayEquals(png.rgba, p3.rgba);
    }

    @Test
    public void ppm3Write() {
        PicData p6 = this._imgTools.fromPPM(this._loadTestResource("micro_canvas_o25_d12.p6.ppm"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        this._imgTools.toPPMP3(p6, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        PicData p3ReadBack = this._imgTools.fromPPM(bais);
        assertArrayEquals(p6.rgba, p3ReadBack.rgba);
    }

    @Test
    public void pgm2Write() {
        PicGrayscale picG = PicGrayscale.create(32, 8);
        for (int i = 0; i < picG.data.length; i++) {
            picG.data[i] = (byte) i;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        this._imgTools.toPGMP2(picG, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        PicGrayscale readBack = this._imgTools.fromPGM(bais);
        assertArrayEquals(picG.data, readBack.data);
    }

    @Test
    public void rgba2bgra() {
        assertEquals(ImgTools.rgba2bgra(0xFF0000FF), 0x0000FFFF);
        assertEquals(ImgTools.rgba2bgra(0x00FF00FF), 0x00FF00FF);
        assertEquals(ImgTools.rgba2bgra(0x0000FFFF), 0xFF0000FF);
    }

    @Test
    public void encToPngThenDecToRaw() {
        PicData picFromP6Pnm = this._imgTools.fromPPM(this._loadTestResource("micro_canvas_o25_d12.p6.ppm"));
        int[] expColors = picFromP6Pnm.rgba;
        PicData picFromPng = picDataToPngAndBack(picFromP6Pnm);
        assertEquals(picFromP6Pnm.width, picFromPng.width);
        assertEquals(picFromP6Pnm.height, picFromPng.height);
        for (int i = 0; i < expColors.length; i++) {
            assertEquals(expColors[i], picFromPng.rgba[i]);
        }
    }

    private PicData picDataToPngAndBack(PicData p) {
        ByteArrayOutputStream baosEncodedPng = new ByteArrayOutputStream();
        this._imgTools.toPNG(p, baosEncodedPng);

        ByteArrayInputStream baisEncodedPng = new ByteArrayInputStream(baosEncodedPng.toByteArray());
        return this._imgTools.fromPNG(baisEncodedPng);
    }

    private InputStream _loadTestResource(String name) {
        String picName = String.format("match_test_pics/%s", name);
        IApkJarAssetLoader ldr = ApkJarAssetLoaderProv.getLoader();
        return ldr.load(picName);
    }

    public ImgToolsTest() {
        this._imgTools = PlatformImgToolsExtProv.getTools();
    }
}
