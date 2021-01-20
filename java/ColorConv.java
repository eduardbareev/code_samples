package com.drscbt.shared.color;

import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclib.PicGrayscale;
import com.drscbt.shared.utils.LoadLib;
import com.drscbt.shared.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColorConv {
    private static Logger _log = LoggerFactory.getLogger(ColorConv.class);
    static abstract class ThreeCompIntColor {
        public int c1, c2, c3;

        public int getInt(byte alpha) {
            return Utils.fourBytesToInt((byte)this.c1, (byte)this.c2, (byte)this.c3, alpha);
        }

        public void setFromInt(int rgba) {
            this.c1 = (rgba >> 24) & 0xFF;
            this.c2 = (rgba >> 16) & 0xFF;
            this.c3 = (rgba >> 8) & 0xFF;
        }

        public boolean equals(Object obj) {
            ThreeCompIntColor clr = (ThreeCompIntColor) obj;
            return ((clr.c1 == this.c1) &&
                    (clr.c2 == this.c2) &&
                    (clr.c3 == this.c3) );
        }

        public ThreeCompIntColor(int c1,int c2, int c3) {
            this.c1 = c1;
            this.c2 = c2;
            this.c3 = c3;
        }

        public ThreeCompIntColor() {}

        public String toString() {
            return String.format("%s(%d, %d, %d)",
                    this.colorModel(), this.c1, this.c2, this.c3);
        }

        abstract public String colorModel() ;
    }

    static abstract public class HsvC extends ThreeCompIntColor {
        public HsvC(int h, int s, int v) { super(h,s,v); }
        public HsvC() {}
    }

    static public class HsvC360_100 extends HsvC {
        public String colorModel() {return "hsv";}
        public HsvC360_100(int h, int s, int v) { super(h,s,v); }
        public HsvC360_100() {}
    }

    static public class HsvC255 extends HsvC {
        public String colorModel() {return "hsv";}
        public HsvC255(int h, int s, int v) { super(h,s,v); }
        public HsvC255() {}
    }

    static public class RgbC extends ThreeCompIntColor {
        public String colorModel() {return "rgb";}
        public RgbC(int r,int g, int b) { super(r,g,b); }
        public RgbC() {}
    }

    static {
        LoadLib.loadLib();
    }

    static public RgbC hsv360_100ToRgb(HsvC360_100 inHsv) {
        RgbC outRgb = new RgbC();
        _hsvToRgb(inHsv, outRgb, 100, 360, 100);
        return outRgb;
    }

    static public RgbC hsv255ToRgb(HsvC255 inHsv) {
        RgbC outRgb = new RgbC();
        _hsvToRgb(inHsv, outRgb, 255, 255, 255);
        return outRgb;
    }

    static private void _hsvToRgb(HsvC inHsv, RgbC outRgb, final int maxSat, final int hueFullCircle, final int maxV ) {
        int iHue = inHsv.c1;
        final int iSatInp = inHsv.c2;
        double fSatNrm, fVNrm, fCircSixthDegr;
        final int iVInp = inHsv.c3;
        int iCircleZone;
        int iOutR, iOutG, iOutB;

        final int rgbMax = 255;
        if (iSatInp == 0)
        {
            int rgbGray = Math.round(255 * ((float)iVInp/maxV));
            iOutR = rgbGray;
            iOutG = rgbGray;
            iOutB = rgbGray;
        } else {
            fSatNrm = iSatInp / (float)maxSat;
            fVNrm = iVInp / (float)maxV;
            float oneSixth = (float)hueFullCircle / 6;
            if (iHue == hueFullCircle) {
                fCircSixthDegr = 0;
            } else {
                fCircSixthDegr = (float)iHue / oneSixth;
            }

            iCircleZone = (int)Math.floor(fCircSixthDegr);

            double fcOffFromSixthBound = fCircSixthDegr - iCircleZone;

            double fp = fVNrm * (1f - fSatNrm);
            double fq = fVNrm * (1f - (fSatNrm * fcOffFromSixthBound));
            double ft = fVNrm * (1f - (fSatNrm * (1f - fcOffFromSixthBound)));

            switch (iCircleZone) {
                case 0:
                    iOutR = (int)Math.round(fVNrm * rgbMax);
                    iOutG = (int)Math.round(ft * rgbMax);
                    iOutB = (int)Math.round(fp * rgbMax);
                    break;
                case 1:
                    iOutR = (int)Math.round(fq * rgbMax);
                    iOutG = (int)Math.round(fVNrm * rgbMax);
                    iOutB = (int)Math.round(fp * rgbMax);
                    break;
                case 2:
                    iOutR = (int)Math.round(fp * rgbMax);
                    iOutG = (int)Math.round(fVNrm * rgbMax);
                    iOutB = (int)Math.round(ft * rgbMax);
                    break;
                case 3:
                    iOutR = (int)Math.round(fp * rgbMax);
                    iOutG = (int)Math.round(fq * rgbMax);
                    iOutB = (int)Math.round(fVNrm * rgbMax);
                    break;
                case 4:
                    iOutR = (int)Math.round(ft * rgbMax);
                    iOutG = (int)Math.round(fp * rgbMax);
                    iOutB = (int)Math.round(fVNrm * rgbMax);
                    break;
                case 5:
                    iOutR = (int)Math.round(fVNrm * rgbMax);
                    iOutG = (int)Math.round(fp * rgbMax);
                    iOutB = (int)Math.round(fq * rgbMax);
                    break;
                default:
                    throw new IllegalStateException("can't happen");
            }
        }
        outRgb.c1 = iOutR;
        outRgb.c2 = iOutG;
        outRgb.c3 = iOutB;
    }
    
    static public void rgbArrToHsv255J(int[] rgb, int[] hsv){
        RgbC inRgb = new RgbC();
        HsvC255 hsvOut = new HsvC255();
        for (int i = 0; i < rgb.length; i++) {
            inRgb.setFromInt(rgb[i]);
            rgbPxToHsv255J(inRgb, hsvOut);
            hsv[i] = hsvOut.getInt((byte)0xFF);
        }
    }

    static public void rgbPxToHsv255J(
            RgbC inRgb, HsvC255 hsvOut
    ) {
        int iHighestRgbCompV;
        int iHiLoRgbCompDiff;
        int iLowestRgbComp;
        int iSaturation;
        int iHue;
        final int maxSat = 255;
        final int hueFullCircle = 255;
        final int maxV = 255;

        final float fOneSixth = hueFullCircle / 6f;
        final int oneThird = Utils.arithmeticHalfUpRound(hueFullCircle / 3f);
        final int twoThirds = Utils.arithmeticHalfUpRound(2f * hueFullCircle / 3f);

        int ir = inRgb.c1;
        int ig = inRgb.c2;
        int ib = inRgb.c3;
        iHighestRgbCompV = Utils.max(ir, ig, ib);
        iLowestRgbComp = Utils.min(ir, ig, ib);
        iHiLoRgbCompDiff = iHighestRgbCompV - iLowestRgbComp;

        if (iHighestRgbCompV == 0) {
            iSaturation = 0;
        } else {
            iSaturation = Utils.arithmeticHalfUpRound((maxSat * iHiLoRgbCompDiff) / (float)iHighestRgbCompV);
        }

        if (iSaturation == 0) {
            iHue = 0;
        } else {
            if (ir == iHighestRgbCompV) {
                iHue = Utils.arithmeticHalfUpRound(( fOneSixth * (ig - ib)) / iHiLoRgbCompDiff);
            } else if (ig == iHighestRgbCompV) {
                iHue = oneThird + Utils.arithmeticHalfUpRound(( fOneSixth * (ib - ir)) / iHiLoRgbCompDiff);
            } else {
                iHue = twoThirds + Utils.arithmeticHalfUpRound(( fOneSixth * (ir - ig)) / iHiLoRgbCompDiff);
            }

            if (iHue < 0) {
                iHue += hueFullCircle;
            }

            if (iHue > hueFullCircle) {
                iHue -= hueFullCircle;
            }

            if (iHue == hueFullCircle) {
                iHue = 0;
            }
        }
        int v = Utils.arithmeticHalfUpRound(((float) iHighestRgbCompV / 255) * maxV);

        hsvOut.c1 = iHue;
        hsvOut.c2 = iSaturation;
        hsvOut.c3 = v;
    }

    static {
        LoadLib.loadLib();
    }

    public static native int rgb2hsvOnePxNaCall(int rgba);
    public static native void rgb2hsvArrNaCall(int rgba[], int hsva[]);

    static public void rgbPxToHsv255Na(RgbC inRgb, HsvC255 outHsv) {
        int rgba = inRgb.getInt((byte) 0xFF);
        int hsv = rgb2hsvOnePxNaCall(rgba);
        outHsv.setFromInt(hsv);
    }

    static public void rgbArrToHsv255Na(
            int[] rgbIn, int[]hsvOut
    ) {
        rgb2hsvArrNaCall(rgbIn, hsvOut);
    }

    static public PicGrayscale grayscale(PicData pic) {
        int[] rgba = pic.rgba;
        PicGrayscale g = PicGrayscale.create(pic.width, pic.height);
        byte[] gray = g.data;

        for (int i = 0; i < rgba.length; i++) {
            gray[i] = _gray(rgba[i]);
        }

        return g;
    }

    static public void grayscale(int[] rgba, byte[] out) {
        for (int i = 0; i < rgba.length; i++) {
            out[i] = _gray(rgba[i]);
        }
    }

    static public void grayscale(PicData src, PicGrayscale dst) {
        for (int i = 0; i < src.rgba.length; i++) {
            dst.data[i] = _gray(src.rgba[i]);
        }
    }

    static public void grayscale(PicData src, PicGrayscale dst, int srcFromX, int srcFromY) {
        for (int y = 0; y < dst.height; y++) {
            int srcOffStride = (y + srcFromY) * src.width;
            int dstOffStride = y * dst.width;
            for (int x = 0; x < dst.width; x++) {
                int sOff = srcOffStride + srcFromX + x;
                int dOff = dstOffStride + x;
                dst.data[dOff] = _gray(src.rgba[sOff]);
            }
        }
    }

    static private byte _gray(int rgbav) {
        int g = (((rgbav >> 8) & 0xFF)
            + ((rgbav >> 16) & 0xFF)
            + ((rgbav >> 24) & 0xFF)) / 3;
        return (byte)g;
    }
}
