package com.drscbt.andrapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.lifecycle.Lifecycle;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import com.drscbt.dapp.R;
import com.drscbt.interactor.AutoListEntry;
import com.drscbt.shared.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DrscbtService extends Service {
    private Logger _log = LoggerFactory.getLogger(DrscbtService.class);

    final static public String AUTOMATION_NAME_KEY = "automation_name";

    private boolean _initialized;

    private volatile Thread _t;

    public volatile boolean botRunning;

    public volatile boolean lastShutdownClean;

    public static DrscbtService instance;

    public volatile List<Exception> lastExceptions;

    public volatile CompletionStatus completionStatus;

    private volatile AutoListEntry _autoEntry;

    public DrscbtService() {
        this._log.debug("DrscbtService.constructor");
        DrscbtService.instance = this;
    }

    private void createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification =
                new NotificationCompat.Builder(this, "noticeChannel1")
                        .setContentTitle("drscbt")
                        .setContentText("drscbt")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(pendingIntent)
                        .setTicker("drscbt")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build();
        startForeground(1, notification);
    }

    public void onCreate() {
        this._log.debug("DrscbtService.onCreate(). instance={}", this);
    }

    private void _init() {
        if (!this._initialized) {
            this.createNotification();
            this._initialized = true;
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        this._log.debug(
            "DrscbtService.onStartCommand(), instance={}. dispatched from threadId = {}",
            this, Thread.currentThread().getId());
        Bundle b = intent.getExtras();
        this._autoEntry = (AutoListEntry) b.getSerializable(AUTOMATION_NAME_KEY);
        this._log.info("automation = \"{}\"", this._autoEntry);
        this._init();
        this._startBot();
        this._log.debug("return onStartCommand");
        return Service.START_STICKY;
    }

    private void _startBot() {
        this.completionStatus = null;
        if (this.botRunning) {
            this._log.debug("_start() rejected (starting or running)");
            return;
        }
        this.lastShutdownClean = true;
        this.botRunning = true;
        this.lastExceptions = new ArrayList<>();
        this._stateUpdate();
        this._log.debug("_start() new Thread");
        this._t = new Thread(this::_threadBody);
        this._t.setName("svcbotwork");
        this._t.start();
    }

    public void stopBot() {
        this._log.debug("setting interrupt flag on {}", this._t.getName());
        this._t.interrupt();
    }

    private void _stateUpdate() {
        MainActivity activity = MainActivity.getInstance();
        if (activity == null) {
            this._log.trace("can't inform activity about state update. (reference is null)");
            return;
        }
        this._log.trace("telling activity {} to reflect state update", activity);
        activity.stateNeedsUpdate();
    }

    private void _threadBody() {
        this._log.debug("_threadBody() started threadId={}", Thread.currentThread().getId());
        Bot bot = null;
        try {
            bot = new Bot();
            bot.work(this._autoEntry);
            this.completionStatus = CompletionStatus.COMPLETE;
            this._log.debug("_threadBody() complete normally");
        } catch (InterruptedException e) {
            this.lastExceptions.add(e);
            this.completionStatus = CompletionStatus.INTERRUPTED;
            this._log.debug("_threadBody() stopped because of InterruptedException. details follows");
            this._log.debug(Utils.getExcpText(e));
        } catch (Exception e) {
            this.lastExceptions.add(e);
            this.completionStatus = CompletionStatus.FAILURE;
            this._log.error("_threadBody() failed. exception follows");
            this._log.error(Utils.getExcpText(e));
        }

        if (bot != null) {
            this._log.debug("shutting down");
            this.lastShutdownClean = bot.shutdown();
            if (this.lastShutdownClean) {
                this._log.info("bot clean shutdown");
            } else {
                this._log.error("bot fails to terminate cleanly");
            }
        }

        this.onThrStop();
    }

    private void onThrStop() {
        this.botRunning = false;
        this._stateUpdate();
        this._bringActivity();
    }

    private void _bringActivity() {
        if ((MainActivity.getInstance() != null) &&
            (MainActivity.getInstance().getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.RESUMED))) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        this.startActivity(intent);
    }

    public void onDestroy() {
        this._log.debug("DrscbtService.onDestroy()");
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    enum CompletionStatus {
        COMPLETE,
        FAILURE,
        INTERRUPTED
    }
}
