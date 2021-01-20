package com.drscbt.shared.piclib;

import com.drscbt.shared.color.ColorConv;

public class PicOps {
    public static void copy(PicData src, PicData dst, int srcFromX, int srcFromY) {
        for (int y = 0; y < dst.height; y++) {
            int srcOffStride = (y + srcFromY) * src.width;
            int dstOffStride = y * dst.width;
            for (int x = 0; x < dst.width; x++) {
                int srcOff = srcOffStride + srcFromX + x;
                int dstOff = dstOffStride + x;
                dst.rgba[dstOff] = src.rgba[srcOff];
            }
        }
    }

    public static void highlight(PicData p, int x, int y) {
        p.rgba[p.width * y + x] = (~p.rgba[p.width * y + x]) | PicData.A_OPAQUE;
    }

    public static void highlight(PicData p, int top, int right, int bottom, int left) {
        for (int y = top; y < bottom; y++) {
            int yOffStride = y * p.width;
            for (int x = left; x < right; x++) {
                int off = yOffStride + x;
                p.rgba[off] = (~p.rgba[off]) | PicData.A_OPAQUE;
            }
        }
    }

    public static PicData copyCCW90(PicData src) {
        PicData dst = PicData.create(src.height, src.width);
        for (int x = 0; x < src.width; x++) {
            for (int y = 0; y < src.height; y++) {
                int srcOff =  (y * src.width) + x;
                int dstOff =  ((dst.height - 1 - x) * dst.width) + y;
                dst.rgba[dstOff] = src.rgba[srcOff];
            }
        }
        return dst;
    }

    public static void replace(PicData p,
        int hFrom, int hTo,
        int sFrom, int sTo,
        int vFrom, int vTo, int replWithRgba
    ) {
        int[] rgba = p.rgba;
        ColorConv.RgbC rgb = new ColorConv.RgbC();
        ColorConv.HsvC255 hsv = new ColorConv.HsvC255();
        for (int i = 0; i < rgba.length; i++) {
            rgb.setFromInt(rgba[i]);
            ColorConv.rgbPxToHsv255Na(rgb, hsv);
            if ((hsv.c1 >= hFrom) && (hsv.c1 <= hTo)
                && (hsv.c2 >= sFrom) && (hsv.c2 <= sTo)
                && (hsv.c3 >= vFrom) && (hsv.c3 <= vTo)) {
                rgba[i] = replWithRgba;
            }
        }
    }
}
