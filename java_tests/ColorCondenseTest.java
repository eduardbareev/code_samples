package com.drscbt.shared.color;

import com.drscbt.shared.assetloader.ApkJarAssetLoaderProv;
import com.drscbt.shared.assetloader.IApkJarAssetLoader;
import com.drscbt.shared.piclib.ImgTools;
import com.drscbt.shared.piclib.Pic8;
import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclib.PlatformImgToolsExtProv;
import com.drscbt.shared.utils.Utils;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ColorCondenseTest {
    private ImgTools _imgTools;

    @Test
    public void inflateFromByte() {
        byte[] arr;

        ColorCondense.TruncConfig tc;
        tc = new ColorCondense.TruncConfig(3, 2, 3);
        tc.setNextBitOnInflate = false;

        arr = ColorCondense.inflateFromByte((byte) 0, tc);
        assertArrayEquals(new byte[]{(byte) 0, (byte) 0, (byte) 0}, arr);

        arr = ColorCondense.inflateFromByte((byte) 0xFF, tc);
        assertArrayEquals(new byte[]{(byte) 0b11100000, (byte) 0b11000000, (byte) 0b11100000}, arr);

        tc = new ColorCondense.TruncConfig(2, 4, 2);
        tc.setNextBitOnInflate = false;
        arr = ColorCondense.inflateFromByte((byte) 0xFF, tc);
        assertArrayEquals(new byte[]{(byte) 0b11000000, (byte) 0b11110000, (byte) 0b11000000}, arr);

        tc = new ColorCondense.TruncConfig(3, 2, 3);
        tc.setNextBitOnInflate = true;

        arr = ColorCondense.inflateFromByte((byte) 0, tc);
        assertArrayEquals(new byte[]{(byte) 0b00010000, (byte) 0b00100000, (byte) 0b00010000}, arr);

        arr = ColorCondense.inflateFromByte((byte) 0xFF, tc);
        assertArrayEquals(new byte[]{(byte) 0b11110000, (byte) 0b11100000, (byte) 0b11110000}, arr);

        tc = new ColorCondense.TruncConfig(2, 4, 2);
        tc.setNextBitOnInflate = true;
        arr = ColorCondense.inflateFromByte((byte) 0xFF, tc);
        assertArrayEquals(new byte[]{(byte) 0b11100000, (byte) 0b11111000, (byte) 0b11100000}, arr);
    }

    @Test
    public void truncToByte() {
        byte cond1;
        byte cond2;

        ColorCondense.TruncConfig tc = new ColorCondense.TruncConfig(3, 2, 3);

        cond1 = ColorCondense.truncToByteJ((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, tc);
        cond2 = ColorCondense.truncToByteJ((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, tc);
        assertEquals((byte) 0xFF, cond1);
        assertEquals((byte) 0xFF, cond2);

        cond1 = ColorCondense.truncToByteJ((byte) 0, (byte) 0, (byte) 0, tc);
        cond2 = ColorCondense.truncToByteJ((byte) 0, (byte) 0, (byte) 0, tc);
        assertEquals(0, cond1);
        assertEquals(0, cond2);

        cond1 = ColorCondense.truncToByteJ((byte) 0b11100000, (byte) 0b11000000, (byte) 0b11100000, tc);
        cond2 = ColorCondense.truncToByteNa((byte) 0b11100000, (byte) 0b11000000, (byte) 0b11100000, tc);
        assertEquals((byte) 0xFF, cond1);
        assertEquals((byte) 0xFF, cond2);

        cond1 = ColorCondense.truncToByteJ((byte) 0b00011111, (byte) 0b00111111, (byte) 0b00011111, tc);
        cond2 = ColorCondense.truncToByteNa((byte) 0b00011111, (byte) 0b00111111, (byte) 0b00011111, tc);
        assertEquals(0, cond1);
        assertEquals(0, cond2);

        cond1 = ColorCondense.truncToByteJ((byte) 0b10100000, (byte) 0b01000000, (byte) 0b01000000, tc);
        cond2 = ColorCondense.truncToByteNa((byte) 0b10100000, (byte) 0b01000000, (byte) 0b01000000, tc);
        assertEquals((byte) 0b10101010, cond1);
        assertEquals((byte) 0b10101010, cond2);

        cond1 = ColorCondense.truncToByteJ((byte) 0b10111111, (byte) 0b01111111, (byte) 0b01011111, tc);
        cond2 = ColorCondense.truncToByteNa((byte) 0b10111111, (byte) 0b01111111, (byte) 0b01011111, tc);
        assertEquals((byte) 0b10101010, cond1);
        assertEquals((byte) 0b10101010, cond2);

        cond1 = ColorCondense.truncToByteJ((byte) 0b01000000, (byte) 0b10000000, (byte) 0b10100000, tc);
        cond2 = ColorCondense.truncToByteNa((byte) 0b01000000, (byte) 0b10000000, (byte) 0b10100000, tc);
        assertEquals((byte) 0b01010101, cond1);
        assertEquals((byte) 0b01010101, cond2);

        cond1 = ColorCondense.truncToByteJ((byte) 0b01011111, (byte) 0b10111111, (byte) 0b10111111, tc);
        cond2 = ColorCondense.truncToByteNa((byte) 0b01011111, (byte) 0b10111111, (byte) 0b10111111, tc);
        assertEquals((byte) 0b01010101, cond1);
        assertEquals((byte) 0b01010101, cond2);
    }

    @Test
    public void condense() {
        PicData p = this._loadPicRes("photo");
        ColorCondense.TruncConfig tc = new ColorCondense.TruncConfig(3, 2, 3);
        assertArrayEquals(ColorCondense.condenseJ(p, tc).data, ColorCondense.condenseNa(p, tc).data);
    }

    @Test
    public void inflate() {
        int w = 1000, h = 1000;
        Pic8 pic8 = new Pic8(new byte[w * h], w, h);
        Random rand = new Random(123);
        rand.nextBytes(pic8.data);

        PicData p2 = PicData.create(w, h);

        ColorCondense.TruncConfig tc = new ColorCondense.TruncConfig(3, 2, 3);
        tc.setNextBitOnInflate = true;

        ColorCondense.expand(pic8, tc, p2);

        byte[] e = new byte[]{0x78, -0x4f, -0x28, -0x43, 0x37, 0x72,
                0x4, -0x45, 0x43, -0x42, 0x68, 0x1f, 0x2a,
                0x3a, -0x76, -0x4c,};
        byte[] s = Utils.md5(p2.rgba);
        assertArrayEquals(e, s);
    }

    private PicData _loadPicRes(String name) {
        String picName = String.format("match_test_pics/%s.png", name);
        IApkJarAssetLoader ldr = ApkJarAssetLoaderProv.getLoader();
        return _imgTools.fromPNG(ldr.load(picName));
    }

    public ColorCondenseTest() {
        this._imgTools = PlatformImgToolsExtProv.getTools();
    }
}
