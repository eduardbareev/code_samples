package com.drscbt.shared.piclib.pnm;

import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclib.PicGrayscale;

import java.io.InputStream;

public class PPMP3RdrPlain extends RdrPlain {
    private int[] _rgba;

    private int _rgbaAcc = 0x000000FF;
    private int _sampleChanNum;

    protected void _processSample(int value) {
        this._rgbaAcc |= value << ((3 - this._sampleChanNum) * 8);
        if (++this._sampleChanNum == 3) {
            this._rgba[this._readPixCnt] = this._rgbaAcc;
            this._rgbaAcc = 0x000000FF;
            this._sampleChanNum = 0;
            this._readPixCnt++;
        }
    }

    public void readInto(PicData p) {
        this._rgba = p.rgba;
        this._read(p.rgba.length * 3);
    }

    public void readInto(PicGrayscale p) {
        throw new UnsupportedOperationException("not implemented");
    }

    public PPMP3RdrPlain(PNM.Header header, InputStream is) {
        super(header, is);
    }
}
