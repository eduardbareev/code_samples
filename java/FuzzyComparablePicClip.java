package com.drscbt.shared.piclib;

public class FuzzyComparablePicClip {
    static public final int SIMILAR = 15;
    private PicGrayscale _pic;

    public FuzzyComparablePicClip(PicGrayscale pic) {
        this._pic = pic;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PicGrayscale op = ((FuzzyComparablePicClip) o)._pic;
        PicGrayscale tp = this._pic;

        if (tp.width != op.width) {
            return false;
        }

        if (tp.height != op.height) {
            return false;
        }

        byte[] tb = tp.data;
        byte[] ob = op.data;

        for (int i = 0; i < tb.length; i++) {
            if (Math.abs((tb[i] & 0xFF) - (ob[i] & 0xFF)) > SIMILAR) {
                return false;
            }
        }

        return true;
    }

    public int hashCode() {
        return this._pic.width * 10000 + this._pic.height;
    }
}
