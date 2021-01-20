package com.drscbt.andrapp;

import com.drscbt.andrapp.conf.ConfLoader;
import com.drscbt.andrapp.conf.StaticConf;
import com.drscbt.andrapp.util.AndroidUtils;
import com.drscbt.andrapp.util.Perm;
import com.drscbt.interactor.AutoListEntry;
import com.drscbt.interactor.AutomationLoader;
import com.drscbt.interactor.CurrScrPicProvider;
import com.drscbt.interactor.CurrScreenPic;
import com.drscbt.interactor.DB;
import com.drscbt.interactor.IAutoExecutor;
import com.drscbt.interactor.PicSaver;
import com.drscbt.interactor.ScreenDumper;
import com.drscbt.screencapture.CapturerSCDPGL;
import com.drscbt.input.InjEvProviderPrivApiReflect;
import com.drscbt.input.InputSimulator;
import com.drscbt.interactor.Interactor;
import com.drscbt.shared.piclocate.Matchers;
import com.drscbt.screencapture.ICapturer;
import com.drscbt.shared.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

class Bot {
    private ICapturer _capturer;
    private Logger _log = LoggerFactory.getLogger(Bot.class);
    private IAutoExecutor _ex;
    private AndroidUtils.DispSize _rDisp = AndroidUtils.getRealDispSize();

    void work(AutoListEntry autoEntry) throws InterruptedException {
        this._capturer = this._getCapturer();

        AutomationLoader aldr = new AutomationLoader();
        this._ex = aldr.load(autoEntry);


        PicSaver picWriter = new PicSaver(StaticConf.getInstance().dumpScreenDir);
        picWriter.clean();

        DB db = null;
        if (autoEntry.usesDb) {
            db = new DB(
                new File(StaticConf.getInstance().scriptsLocalDataDir,
                    this._ex.getName()),
                this._ex.getMigrations(),
                ConfLoader.getConf().sqliteJournalMode);
            db.connectAndInit();
        }

        CurrScreenPic currScreenPic = new CurrScreenPic(this._capturer);
        CurrScrPicProvider currScreenPicProvider = new CurrScrPicProvider();
        currScreenPicProvider.setCurrScrPic(currScreenPic);

        ScreenDumper scrDumper = new ScreenDumper(currScreenPicProvider, picWriter);

        Interactor interactor = new Interactor(
            currScreenPicProvider,
            scrDumper,
            new Matchers(),
            new InputSimulator(
                new InjEvProviderPrivApiReflect(),
                AndroidUtils.getDispDensity()
            ),
            this._ex.getPicLoader(),
            this._ex.getLocalDataLoader(),
            this._ex.getLogName(),
            this._rDisp,
            db
        );

        this._log.info(Utils.repeat("-", 25));
        this._log.info("automation start");

        try {
            this._ex.execute(interactor);
        } catch (Exception e) {
            try {
                scrDumper.lastCapture();
            } catch (InterruptedException e2) {
                // it's a shutdown phase
                Thread.currentThread().interrupt();
            } catch (Exception e2) {
                this._log.error(Utils.getExcpText(e2));
            }
            throw e;
        }

        this._log.info("automation end");
        this._log.info(Utils.repeat("-", 25));
    }

    boolean shutdown() {
        if (this._capturer == null) {
            return true;
        }

        try {
            this._capturer.shutdown();
            return true;
        } catch (Exception e) {
            this._log.error(Utils.getExcpText(e));
            return false;
        }
    }

    private ICapturer _getCapturer() throws InterruptedException {
        Perm.checkPermThrow(CapturerSCDPGL.PERMISSION_REQUIRED);
        return new CapturerSCDPGL(this._rDisp.w, this._rDisp.h);
    }
}
