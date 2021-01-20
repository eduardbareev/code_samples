package com.drscbt.shared.piclocate.split;

import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclocate.MaskConf;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TrivialSplitter implements ITrivialSplitter {

    private State _state;
    private int _bStartIdx;
    private int _gapLinesCnt;
    private Set<Segment1D> _results;
    private int _edgeGapStrict;
    private int _gapThickness;

    private int _sLenMin;
    private int _sLenMax;

    public Set<Segment2D> doubleSplit(
        PicData pic, MaskConf m,
        int bgRgba, int channelErr,
        int gapThickness,
        int edgeGapStrict,
        Axis firstPassAxis, Axis secondPassAxis,
        int fpSLenMin, int fpSLenMax,
        int spSLenMin, int spSLenMax
    ) {
        return this.splitAs2d(pic, m, bgRgba, channelErr, gapThickness,
            edgeGapStrict, firstPassAxis, fpSLenMin, fpSLenMax)
            .stream().map(s -> this.splitAs2d(pic, this._segmToMask(s), bgRgba,
                channelErr, gapThickness, edgeGapStrict, secondPassAxis,
                spSLenMin, spSLenMax))
            .flatMap(s -> s.stream()).collect(Collectors.toSet());
    }

    private MaskConf _segmToMask(Segment2D s) {
        return new MaskConf(s.xStart, s.yStart, s.xLength, s.yLength);
    }

    public Set<Segment2D> splitAs2d(
        PicData pic, MaskConf m,
        int bgRgba, int channelErr,
        int gapThickness,
        int edgeGapStrict, Axis axis,
        int sLenMin, int sLenMax) {
        Set<Segment1D> segments
            = this.split(pic, m, bgRgba, channelErr,
            gapThickness, edgeGapStrict, axis, sLenMin, sLenMax);

        int xStart = (m == null) ? 0 : m.fromX;
        int xLen = (m == null) ? pic.width : m.width;
        int yStart = (m == null) ? 0 : m.fromY;
        int yLen = (m == null) ? pic.height : m.height;

        return segments.stream()
            .map(s1 -> {
                if (axis == Axis.V) {
                    return new Segment2D(xStart, xLen, s1.start, s1.len);
                } else {
                    return new Segment2D(s1.start, s1.len, yStart, yLen);
                }
            })
            .collect(Collectors.toSet());
    }

    public Set<Segment1D> split(
            PicData pic, MaskConf m,
            int bgRgba, int channelErr,
            int gapThickness,
            int edgeGapStrict,
            Axis axis, int sLenMin, int sLenMax) {
        this._results = new HashSet<Segment1D>();
        this._edgeGapStrict = edgeGapStrict;
        this._gapThickness = gapThickness;
        int[] rgba = pic.rgba;
        this._sLenMin = sLenMin;
        this._sLenMax = sLenMax;

        int runDimFrom;
        int otherDimFrom;
        int runDimTo;
        int otherDimTo;
        int picWidth;

        picWidth = pic.width;
        if (axis == Axis.V) {
            runDimFrom = (m != null) ? m.fromY : 0;
            runDimTo = (m != null) ? m.fromY + m.height : pic.height;
            otherDimFrom = (m != null) ? m.fromX : 0;
            otherDimTo = (m != null) ? m.fromX + m.width : pic.width;
        } else {
            runDimFrom = (m != null) ? m.fromX : 0;
            runDimTo = (m != null) ? m.fromX + m.width : pic.width;
            otherDimFrom = (m != null) ? m.fromY : 0;
            otherDimTo = (m != null) ? m.fromY + m.height : pic.height;
        }

        this._run(runDimFrom, runDimTo,
                otherDimFrom, otherDimTo,
                picWidth, rgba, bgRgba, channelErr, axis);
        return this._results;
    }

    private void _run(int runDimFrom, int runDimTo,
                      int otherDimFrom, int otherDimTo,
                      int picWidth, int[] rgba, int bgRgba,
                      int channelErr, Axis axis) {
        Line nline;
        this._bStartIdx = -1;
        this._gapLinesCnt = 0;
        this._state = State.START;

        int runDimIdx = runDimFrom;
        while (this._state != State.STOP) {
            if (runDimIdx == runDimTo) {
                nline = Line.END;
            } else {
                int otherDimIdx;
                for (otherDimIdx = otherDimFrom; otherDimIdx < otherDimTo; otherDimIdx++) {
                    int off;
                    if (axis == Axis.V) {
                        off = (runDimIdx * picWidth) + otherDimIdx;
                    } else {
                        off = (otherDimIdx * picWidth) + runDimIdx;
                    }
                    int v = rgba[off];
                    if (!this._eq(bgRgba, v, channelErr)) {
                        break;
                    }
                }
                nline = (otherDimIdx == otherDimTo) ? Line.BG : Line.NON_BG;
            }
            this._handleLine(nline, runDimIdx);
            runDimIdx++;
        }
    }

    private void _handleLine(Line nline, int runDimIdx) {
        int sLen;
        boolean sLenMatchesReq;
        boolean constitutesSegment;
        switch (nline) {
            case END:
                constitutesSegment = (((this._edgeGapStrict & TRAIL) == 0)
                    && (_state == State.READING_SOMETHING));
                if (!constitutesSegment) {
                    this._state = State.STOP;
                    break;
                }
                sLen = runDimIdx - this._bStartIdx - this._gapLinesCnt;
                sLenMatchesReq = ((sLen >= this._sLenMin)
                    && ((this._sLenMax == 0) || (sLen <= this._sLenMax)));
                if (!sLenMatchesReq) {
                    this._state = State.STOP;
                    break;
                }

                this._results.add(new Segment1D(this._bStartIdx, sLen));
                this._state = State.STOP;
                break;
            case BG:
                if ((++this._gapLinesCnt) != this._gapThickness)  {
                    break;
                }
                this._state = State.VALID_GAP_READ;

                constitutesSegment = this._bStartIdx != -1;
                if (!constitutesSegment) {
                    break;
                }
                sLen = runDimIdx - this._bStartIdx - this._gapLinesCnt + 1;
                sLenMatchesReq = ((sLen >= this._sLenMin)
                        && ((this._sLenMax == 0) || (sLen <= this._sLenMax)));

                if (sLenMatchesReq) {
                    this._results.add(new Segment1D(this._bStartIdx, sLen));
                }

                break;
            case NON_BG:
                this._gapLinesCnt = 0;
                if (this._state == State.VALID_GAP_READ) {
                    this._bStartIdx = runDimIdx;
                } else if (((this._edgeGapStrict & LEAD) == 0)
                    && this._state == State.START) {
                    this._bStartIdx = runDimIdx;
                }
                this._state = State.READING_SOMETHING;
                break;
        }
    }

    private boolean _eq(int a, int b, int channelErr) {
        for (int i = 0; i < 3; i++) {
            int d = ((a >>= 8) & 0xFF) - ((b >>= 8) & 0xFF);
            if (Math.abs(d) > channelErr) {
                return false;
            }
        }
        return true;
    }

    enum Line { END, NON_BG, BG}

    enum State { START, STOP, READING_SOMETHING, VALID_GAP_READ }

    public enum Axis { H, V }
}
