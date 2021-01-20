package com.drscbt.input;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputSimulator {
    static private final double M_NUM = 2.5;
    private Logger _log = LoggerFactory.getLogger(InputSimulator.class);
    private IInjEvProvider injEvProvider;
    private int _dispDensity;
    public InputSimulator(IInjEvProvider injEvProvider, int dispDensity){
        this.injEvProvider = injEvProvider;
        this._dispDensity = dispDensity;
    }

    public void tap(int x, int y, int pauseMs) throws InterruptedException {
        long t1 = SystemClock.uptimeMillis();
        MotionEvent ev1 = MotionEvent.obtain(t1,t1,MotionEvent.ACTION_DOWN,x,y,0);
        ev1.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        this.injEvProvider.injectInputEvent(ev1, 2);
        this._sleep(pauseMs);
        long t2 = SystemClock.uptimeMillis();
        MotionEvent ev2 = MotionEvent.obtain(t2,t2,MotionEvent.ACTION_UP,x,y,0);
        ev2.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        this.injEvProvider.injectInputEvent(ev2, 2);
    }

    private void _injUpEv(long downTime, int x, int y, int mode) {
        MotionEvent ev;
        long t = SystemClock.uptimeMillis();
        ev = MotionEvent.obtain(downTime, t, MotionEvent.ACTION_UP, x, y,0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        this.injEvProvider.injectInputEvent(ev, mode);
    }

    private long _injDownEv(int x, int y, int mode) {
        MotionEvent ev;
        long t = SystemClock.uptimeMillis();
        ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y,0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        this.injEvProvider.injectInputEvent(ev, mode);
        return t;
    }

    private void _injMvEv(long downTime, int x, int y, int mode) {
        MotionEvent ev;
        long t = SystemClock.uptimeMillis();
        ev = MotionEvent.obtain(downTime, t, MotionEvent.ACTION_MOVE,x,y,0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        this.injEvProvider.injectInputEvent(ev, mode);
    }

    public void hMove(int x, int y, int offsetY) throws InterruptedException {
        int stpW = 20;
        offsetY += 16 * Math.signum(offsetY);
        int dist = Math.abs(offsetY);
        if (dist < 100) {
            throw new IllegalArgumentException("distance must be >=100px");
        }
        int nSteps = Math.max(dist / stpW, 17);
        int yStep = offsetY / nSteps;
        int currY = y;
        long dTime = this._injDownEv(x, currY, 0);

        for (int sn = 1; sn <= nSteps; sn++) {
            currY = y + (yStep * sn);
            this._injMvEv(dTime, x, currY, 1);
            this._sleep(5);
        }

        currY = y + offsetY;

        this._sleep(70);
        this._injMvEv(dTime, x, currY, 2);
        this._injUpEv(dTime, x, currY, 2);
    }


    public void scrollLine(int x, int y, float lines) {
        MotionEvent.PointerCoords coord = new MotionEvent.PointerCoords();
        coord.x = x;
        coord.y = y;
        coord.setAxisValue(MotionEvent.AXIS_VSCROLL, lines);
        coord.setAxisValue(MotionEvent.AXIS_HSCROLL, 0);
        coord.orientation = 0;
        coord.size = 1;
        MotionEvent.PointerCoords[] coords = { coord };

        MotionEvent.PointerProperties[] props = {new MotionEvent.PointerProperties()};
        props[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        props[0].id = 0;

        MotionEvent ev = MotionEvent.obtain(
                SystemClock.uptimeMillis()-100, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_SCROLL,
                1, props, coords, 0,
                0, 1f, 1f,
                -1, 0,
                InputDevice.SOURCE_MOUSE, 0
        );

        this.injEvProvider.injectInputEvent(ev,0);
    }

    public void scrollPx(int x, int y, int distPx) {
        int distPxAbs = Math.abs(distPx);
        int distPxA = distPxAbs;
        int distPxB = 0;
        float sign = Math.signum(distPx);
        double distLinesA;
        double distLinesB;

        for (;;) {
            distLinesA = (float) (distPxA * M_NUM / (double) this._dispDensity);
            distLinesB = (float) (distPxB * M_NUM / (double) this._dispDensity);

            int sumPx = ((int) (distLinesA * this._dispDensity / M_NUM))
                + ((int) (distLinesB * this._dispDensity / M_NUM));

            if (sumPx == distPxAbs) {
                break;
            } else {
                if (distPxA == 0) {
                    throw new RuntimeException("can't choose numbers for wheel pixel scroll");
                }
                distPxA--;
                distPxB++;
            }
        }

        distLinesA *= sign;
        distLinesB *= sign;

        this.scrollLine(x, y, (float) distLinesA);
        if (distLinesB != 0) {
            this.scrollLine(x, y, (float) distLinesB);
        }
    }

    public int scrollLinesToPx(double lines) {
        return (int) (lines * this._dispDensity / M_NUM);
    }

    private void _sleep(int millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    public void pressKey(int keycode, int downUpPause) throws InterruptedException {
        this._pressBtn(keycode, downUpPause);
    }

    public void pressKey(int keycode) throws InterruptedException {
        this._pressBtn(keycode, 100);
    }

    public void sendText(final String text) throws InterruptedException {
        final char[] chars = text.toCharArray();
        final KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        final KeyEvent[] events = kcm.getEvents(chars);
        for (int i = 0; i < events.length; i++) {
            KeyEvent e = events[i];
            e.setSource(InputDevice.SOURCE_KEYBOARD);
            this.injEvProvider.injectInputEvent(e, 2);
            _sleep(100);
        }
    }

    private void _pressBtn(int keycode, int downUpPause) throws InterruptedException {
        long t = SystemClock.uptimeMillis();
        KeyEvent ke1 = new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keycode,0);
        ke1.setSource(InputDevice.SOURCE_KEYBOARD);
        this.injEvProvider.injectInputEvent(ke1, 2);
        this._sleep(downUpPause);
        long t2 = SystemClock.uptimeMillis();
        KeyEvent ke2 = new KeyEvent(t2, t2, KeyEvent.ACTION_UP, keycode,0);
        ke2.setSource(InputDevice.SOURCE_KEYBOARD);
        this.injEvProvider.injectInputEvent(ke2, 2);
    }

    private void _pressBtn(int keycode) throws InterruptedException {
        this._pressBtn(keycode, 100);
    }
}
