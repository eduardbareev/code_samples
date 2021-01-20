package com.drscbt.screencapture;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class SurfaceFrameReader implements SurfaceTexture.OnFrameAvailableListener {
    private Logger _log = LoggerFactory.getLogger(SurfaceFrameReader.class);
    public Surface surface;
    final private Object _initializedSyncObj = new Object();
    private int _scrW;
    private int _scrH;
    private boolean _newFrameAvailable = false;
    private final Object _glThreadMonitor = new Object();
    private boolean _readRequest = false;
    private ByteBuffer _pic;
    private boolean _readComplete;
    private final Object _readCompleteSyncObj = new Object();
    private GLOffscreenSurface _gloss;

    private volatile Thread _onFrmAvailLooperThread;
    private volatile Thread _glThread;

    private Looper _onFrmAvailLooper;
    private final Object _frmAvailThrdReadyLock = new Object();

    private boolean _firstFrameArrived = false;
    private final Object _firstFrameArrivalMonitor = new Object();

    public ByteBuffer getNewFrame() throws InterruptedException {
        synchronized (this._glThreadMonitor) {
            this._readRequest = true;
            this._glThreadMonitor.notify();
        }

        this._readComplete = false;
        synchronized (this._readCompleteSyncObj) {
            while (!this._readComplete) {
                this._readCompleteSyncObj.wait();
            }
            return this._pic;
        }
    }

    public void onFrameAvailable(SurfaceTexture st) {
        if (!this._firstFrameArrived) {
            this._firstFrameArrived = true;
            this._log.debug("onFrameAvailable: _first_frame_arrived");
            synchronized (this._firstFrameArrivalMonitor) {
                this._firstFrameArrivalMonitor.notify();
            }
        }

        synchronized (_glThreadMonitor) {
            this._newFrameAvailable = true;
            this._glThreadMonitor.notify();
        }
    }

    public void waitForFirstFrame() throws InterruptedException {
        synchronized (this._firstFrameArrivalMonitor) {
            while (!this._firstFrameArrived) {
                this._firstFrameArrivalMonitor.wait();
            }
        }
    }

    private void _glThreadBody() {
        this._gloss = new GLOffscreenSurface(this._scrW, this._scrH);

        this.surface = this._gloss.surface;
        synchronized (this._initializedSyncObj) {
            this._initializedSyncObj.notify();
        }

        synchronized (this._glThreadMonitor) {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    this._log.debug("isInterrupted() flag in gl loop");
                    break;
                }

                try {
                    this._glThreadMonitor.wait();
                } catch (InterruptedException e) {
                    this._log.debug("InterruptedException thrown from wait() in gl loop");
                    Thread.currentThread().interrupt();
                    break;
                }

                if (this._newFrameAvailable) {
                    this._gloss.onFrameAvailable();
                    this._newFrameAvailable = false;
                }

                if (this._readRequest) {
                    this._readRequest = false;
                    this._pic = this._gloss.readPixels();
                    synchronized (this._readCompleteSyncObj) {
                        this._readComplete = true;
                        this._readCompleteSyncObj.notify();
                    }
                }
            }
        }
        this._log.debug("glThreadBody loop is complete, returning from _glThreadBody()");
    }

    private void _glThreadExcpHndlr(Thread t, Throwable e) {
        this._log.error("exception in SurfaceFrameReader.glThreadBody. stack trace follows");
        this._log.error(Utils.getExcpText(e));
        try {
            this._shutdownFrmAvailLooperThread();
        } catch (ScreenCaptureShutdownException ex) {
            this._log.error(Utils.getExcpText(ex));
        }
    }

    private void _setUpGlThrd() throws InterruptedException {
        this._glThread = new Thread(this::_glThreadBody);
        this._glThread.setName("sfrglthr");
        this._glThread.setUncaughtExceptionHandler(this::_glThreadExcpHndlr);
        this._glThread.start();

        synchronized (this._initializedSyncObj) {
            while (this.surface == null) {
                this._initializedSyncObj.wait();
            }
        }
    }

    private void _onFrmAvailThrdBody() {
        Looper.prepare();
        this._onFrmAvailLooper = Looper.myLooper();

        synchronized (this._frmAvailThrdReadyLock) {
            this._frmAvailThrdReadyLock.notify();
        }

        Looper.loop();
    }

    private void _setUpFrameAvailThrd() throws InterruptedException {
        this._onFrmAvailLooperThread = new Thread(this::_onFrmAvailThrdBody);
        this._onFrmAvailLooperThread.setName("onFrmAvLstnrLpr");
        this._onFrmAvailLooperThread.start();

        synchronized (this._frmAvailThrdReadyLock) {
            while (this._onFrmAvailLooper == null) {
                this._frmAvailThrdReadyLock.wait();
            }
        }

        this._gloss._setOnFrameAvailableListener(
            this,
            new Handler(this._onFrmAvailLooper)
        );
    }

    public SurfaceFrameReader(int scrW, int scrH) throws InterruptedException {
        this._scrW = scrW;
        this._scrH = scrH;
        this._setUpGlThrd();
        this._setUpFrameAvailThrd();
    }

    public void shutdown() throws ScreenCaptureShutdownException {
        if (this._glThread != null) {
            synchronized (this._glThreadMonitor) {
                this._glThread.interrupt();
            }
            if (!Utils.joinUninterruptibly(this._glThread, 2)) {
                throw new ScreenCaptureShutdownException(String.format("thread %s doesn't terminate", this._glThread.getName()));
            } else {
                this._log.debug("thread {} termination confirmed", this._glThread.getName());
            }
        }

        this._shutdownFrmAvailLooperThread();

        this._log.debug("SurfaceFrameReader: clean shutdown");
    }

    private void _shutdownFrmAvailLooperThread() throws ScreenCaptureShutdownException {
        if (this._onFrmAvailLooperThread != null) {
            this._gloss._removeOnFrameAvailableListener();
            this._onFrmAvailLooper.quit();
            if (!Utils.joinUninterruptibly(this._onFrmAvailLooperThread, 2)) {
                throw new ScreenCaptureShutdownException(String.format("thread %s doesn't terminate", this._onFrmAvailLooperThread.getName()));
            } else {
                this._log.debug("thread {} termination confirmed", this._onFrmAvailLooperThread.getName());
            }
        }
    }
}
