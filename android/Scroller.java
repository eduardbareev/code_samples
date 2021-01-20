package com.drscbt.interactor;

import android.os.SystemClock;

import com.drscbt.andrapp.util.AndroidUtils;
import com.drscbt.auto_iface.CtrlScrlResCode;
import com.drscbt.auto_iface.IAreaAbsPx;
import com.drscbt.auto_iface.ICtrlScrlRes;
import com.drscbt.auto_iface.IPoint;
import com.drscbt.auto_iface.IScroller;
import com.drscbt.input.InputSimulator;
import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclocate.Matchers;
import com.drscbt.shared.piclocate.scrollfinder.ScrollInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scroller implements IScroller {
    private Logger _log = LoggerFactory.getLogger(Scroller.class);
    private PicData _scrlPrevScreen;
    private int _scrlPrevScreenSerial;
    private Interactor _inter;
    private InputSimulator _inpSimulator;
    private Matchers _matchers;
    private CurrScrPicProvider _currScreenPicProvider;
    private ScreenDumper _scrDumper;
    private AndroidUtils.DispSize _dispSize;

    public Scroller(Interactor inter,
                    InputSimulator inpSimulator,
                    Matchers matchers,
                    CurrScrPicProvider cScreenPic,
                    ScreenDumper scrDumper,
                    AndroidUtils.DispSize dispSize) {
        this._inter = inter;
        this._inpSimulator = inpSimulator;
        this._matchers = matchers;
        this._currScreenPicProvider = cScreenPic;
        this._scrDumper = scrDumper;
        this._dispSize = dispSize;
        this._scrlPrevScreen = PicData.create(
            this._currScreenPicProvider.getCurrScrPic().getWidth(),
            this._currScreenPicProvider.getCurrScrPic().getHeight()
        );
    }

    private void _checkPre() throws InterruptedException {
        PicData pic = this._currScreenPicProvider.getCurrScrPic().capture();
        this._scrlPrevScreenSerial = this._currScreenPicProvider.getCurrScrPic().serial();

        System.arraycopy(pic.rgba, 0,
                this._scrlPrevScreen.rgba, 0,
                pic.rgba.length);
    }

    private CtrlScrlRes _checkPost(int expectedOffsetPx, AreaAbsPx area) throws InterruptedException {
        ScrollInfo si;
        CtrlScrlRes csr = null;
        int chkTo = 2500;
        long chkTimeStart = SystemClock.uptimeMillis();
        int chkElapsed = 0;
        PicData pic = null;
        int picSerial = -1;
        while (chkElapsed <= chkTo) {
            pic = this._currScreenPicProvider.getCurrScrPic().capture();
            picSerial = this._currScreenPicProvider.getCurrScrPic().serial();
            si = this._matchers.scrollOffset(this._scrlPrevScreen, pic, area.maskConf());
            csr = this._scrlResFromScrlInfo(expectedOffsetPx, si);
            if (csr.result == CtrlScrlResCode.AS_EXPECTED) {
                break;
            }
            chkElapsed = (int) (SystemClock.uptimeMillis() - chkTimeStart);
        }

        if ((csr.result == CtrlScrlResCode.DISPARATE)
                || (csr.result == CtrlScrlResCode.OPPOSITE)
                || (csr.result == CtrlScrlResCode.STILL)) {
            this._log.error("returning {}", csr);
            this._log.error(area.toString());
            this._scrDumper.image(this._scrlPrevScreen, this._scrlPrevScreenSerial, "prev");
            if (pic != null) {
                this._scrDumper.image(pic, picSerial, "next");
            }
            this._scrDumper.lastCaptureHighlight(area, "scrl_a");
        }

        return csr;
    }


    public ICtrlScrlRes scroll(IPoint oPnt, double offset, Unit u, Mode m, IAreaAbsPx checkAreai) throws InterruptedException {
        if (offset == 0) {
            throw new IllegalArgumentException("offset = " + offset);
        }

        Point o = this._getOrigin(oPnt, checkAreai);
        int ox = o.x;
        int oy = o.y;

        this._checkPre();
        int expectedOffsetPx = this._call(u, m, offset, ox, oy);
        return this._checkPost(expectedOffsetPx, (AreaAbsPx) checkAreai);
    }

    // positive offset - "read further"; negative - "go back"
    public void scroll(IPoint oPnt, double offset, Unit u, Mode m) throws InterruptedException {
        if (offset == 0) {
            throw new IllegalArgumentException("offset = " + offset);
        }

        Point o = (Point) oPnt;
        this._call(u, m, offset, o.x, o.y);
    }

    private Point _getOrigin(IPoint oPnt, IAreaAbsPx checkAreaI) {
        AreaAbsPx checkArea = (AreaAbsPx) checkAreaI;
        if (oPnt != null) {
            return (Point)oPnt;
        } else {
            if (checkArea != null) {
                return checkArea.getCenter();
            } else {
                throw new IllegalArgumentException("both oPnt and checkArea are null");
            }
        }
    }

    private int _call(Unit u, Mode m, double offset, int ox, int oy) throws InterruptedException {
        if (u == Unit.PX && m == Mode.TOUCH) {
            this._inpSimulator.hMove(ox, oy, (int) -offset);
            return  (int) -offset;
        } else if (u == Unit.PX && m == Mode.WHEEL) {
            this._inpSimulator.scrollPx(ox, oy, (int) -offset);
            return (int) -offset;
        } else if (u == Unit.LINES && m == Mode.WHEEL) {
            this._inpSimulator.scrollLine(ox, oy, (float) -offset);
            return this._inpSimulator.scrollLinesToPx(-offset);
        } else {
            throw new IllegalArgumentException(
                    String.format("scroll %s with %s is not supported", m, u));
        }
    }

    private CtrlScrlRes _scrlResFromScrlInfo(int offset, ScrollInfo si) {
        if (offset == 0) {
            throw new IllegalArgumentException("offset == 0");
        }

        CtrlScrlResCode result;
        int missPxAbs = 0;
        if (si == null) {
            result = CtrlScrlResCode.DISPARATE;
        } else if (si.direction == ScrollInfo.Direction.NO_SCROLL) {
            result = CtrlScrlResCode.STILL;
        } else if (((offset < 0) && (si.direction == ScrollInfo.Direction.SCROLL_DOWN_SURFACE_MV_UP))
                || ((offset > 0) && (si.direction == ScrollInfo.Direction.SCROLL_UP_SURFACE_MV_DOWN))) {
            if (si.absOffset == Math.abs(offset)) {
                result = CtrlScrlResCode.AS_EXPECTED;
            } else if (si.absOffset > Math.abs(offset)) {
                missPxAbs = si.absOffset - Math.abs(offset);
                result = CtrlScrlResCode.OVERSHOT;
            } else {
                missPxAbs = Math.abs(offset) - si.absOffset;
                result = CtrlScrlResCode.UNDERSHOT;
            }
        } else {
            result = CtrlScrlResCode.OPPOSITE;
        }
        return new CtrlScrlRes(result, si, missPxAbs, offset);
    }
}
