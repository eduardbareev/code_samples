package com.drscbt.shared.color;

import com.drscbt.shared.piclib.Pic8;
import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclib.PicGrayscale;
import com.drscbt.shared.utils.LoadLib;
import com.drscbt.shared.utils.Utils;

public class ColorCondense {
    static Pic8 condenseJ(PicData picDataRgba, ColorCondense.TruncConfig tc) {
        ColorConv.HsvC255 hsv = new ColorConv.HsvC255();
        ColorConv.RgbC rgb = new ColorConv.RgbC();
        Pic8 pic8 = new Pic8(new byte[picDataRgba.width * picDataRgba.height], picDataRgba.width, picDataRgba.height);
        for (int i = 0; i < picDataRgba.rgba.length; i++) {
            rgb.setFromInt(picDataRgba.rgba[i]);
            ColorConv.rgbPxToHsv255J(rgb, hsv);
            pic8.data[i] = ColorCondense.truncToByteJ((byte)hsv.c1, (byte)hsv.c2, (byte)hsv.c3, tc);
        }
        return pic8;
    }

    public static Pic8 condenseNa(PicData picDataRgba, TruncConfig tc) {
        Pic8 pic8 = new Pic8(new byte[picDataRgba.width * picDataRgba.height], picDataRgba.width, picDataRgba.height);
        condenseIntoNa(picDataRgba, tc, pic8);
        return pic8;
    }

    private static void condenseIntoNa(PicData picDataRgba, TruncConfig tc, Pic8 pic8) {
        if (pic8.data.length != picDataRgba.rgba.length) {
            throw new RuntimeException("pic8.data.length !== picDataRgba.rgba.length");
        }
        condenseNaCall(picDataRgba.rgba, pic8.data, tc.bits1, tc.bits2, tc.bits3);
        pic8.width = picDataRgba.width;
        pic8.height = picDataRgba.height;
    }

    native static void condenseNaCall(int[] rgb, byte[]pic8Out, int bits1, int bits2, int bits3);

    static void expand(Pic8 hsv8, ColorCondense.TruncConfig tc, PicData destRgbPicdata) {
        ColorConv.RgbC rgb24;
        ColorConv.HsvC255 hsv24 = new ColorConv.HsvC255();
        for (int i = 0; i < hsv8.data.length; i++) {
            ColorCondense.inflateFromByte(hsv24, hsv8.data[i], tc);
            rgb24 = ColorConv.hsv255ToRgb(hsv24);
            destRgbPicdata.rgba[i] = rgb24.getInt((byte) PicData.A_OPAQUE);
        }
    }

    static byte truncToByteNa(byte cIn1, byte cIn2, byte cIn3, TruncConfig truncConfig) {
        return truncToByteNaCall(cIn1, cIn2, cIn3, truncConfig.bits1, truncConfig.bits2, truncConfig.bits3);
    }

    static byte truncToByteJ(byte cIn1, byte cIn2, byte cIn3, TruncConfig truncConfig) {
        return truncToByteJ(cIn1, cIn2, cIn3, truncConfig.bits1, truncConfig.bits2, truncConfig.bits3);
    }

    private static byte truncToByteJ(byte cIn1, byte cIn2, byte cIn3, int bits1, int bits2, int bits3) {
        chkTruncBits(bits1, bits2, bits3);
        int cOut1 = cIn1 & (((1 << bits1) - 1) << (8 - bits1));
        int cOut2 = (cIn2 >> bits1) & (((1 << bits2) - 1) << (8 - bits1 - bits2));
        int cOut3 = (cIn3 >> (bits1 + bits2)) & ((1 << bits3) - 1);
        return (byte) (cOut1 + cOut2 + cOut3);
    }

    native public static byte truncToByteNaCall(byte cIn1, byte cIn2, byte cIn3, int bits1, int bits2, int bits3);

    static {
        LoadLib.loadLib();
    }

    private static void inflateFromByte(ColorConv.HsvC255 dest, byte b, TruncConfig truncConfig) {
        byte[] res = inflateFromByte(b, truncConfig.bits1, truncConfig.bits2, truncConfig.bits3, truncConfig.setNextBitOnInflate);
        dest.c1 = Utils.toUnsignedInt(res[0]);
        dest.c2 = Utils.toUnsignedInt(res[1]);
        dest.c3 = Utils.toUnsignedInt(res[2]);
    }

    static byte[] inflateFromByte(byte b, TruncConfig tc) {
        return inflateFromByte(b, tc.bits1, tc.bits2, tc.bits3, tc.setNextBitOnInflate);
    }

    private static byte[] inflateFromByte(byte b, int bits1, int bits2, int bits3, boolean setNextBit) {
        chkTruncBits(bits1, bits2, bits3);
        byte c1 = (byte)(b & (((1 << bits1) - 1) << (8 - bits1)));
        byte c2 = (byte)((b & (((1 << bits2) - 1) << (8 - bits1 - bits2))) << bits1);
        byte c3 = (byte)((b & ((1 << bits3) - 1)) << (bits1 + bits2));

        if (setNextBit) {
            c1 |= 1 << (8 - bits1 - 1);
            c2 |= 1 << (8 - bits2 - 1);
            c3 |= 1 << (8 - bits3 - 1);
        }

        return new byte[]{c1, c2, c3};
    }

    public static PicData inflateFromGrayscale(PicGrayscale gray) {
        PicData color = PicData.create(gray.width, gray.height);
        for (int i = 0; i < gray.data.length; i++) {
            int v = gray.data[i] & 0xFF;
            color.rgba[i] = (v << 8) + (v << 16) + (v << 24) + PicData.A_OPAQUE;
        }
        return color;
    }

    private static void chkTruncBits(int bits1, int bits2, int bits3) {
        if ((bits1 + bits2 + bits3) != 8) {
            throw new IllegalArgumentException("sum of bits should be equal to 8");
        }
    }

    public static class TruncConfig {
        int bits1;
        int bits2;
        int bits3;
        boolean setNextBitOnInflate;
        public TruncConfig(int bits1, int bits2, int bits3) {
            this.bits1 = bits1;
            this.bits2 = bits2;
            this.bits3 = bits3;
            this.setNextBitOnInflate = true;
        }
        public static TruncConfig getDefault() {
            return new TruncConfig(3,2,3);
        }
    }
}
