package com.drscbt.andrapp;

import android.app.Activity;
import android.content.Intent;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.drscbt.andrapp.conf.Conf;
import com.drscbt.andrapp.conf.ConfLoader;
import com.drscbt.andrapp.conf.StaticConf;
import com.drscbt.andrapp.conf.DevDBInfo;
import com.drscbt.andrapp.util.AndroidUtils;
import com.drscbt.andrapp.util.Perm;
import com.drscbt.dapp.R;
import com.drscbt.input.InjEvProviderPrivApiReflect;
import com.drscbt.interactor.AutoListEntry;
import com.drscbt.interactor.AutomationLoader;
import com.drscbt.screencapture.CapturerSCDPGL;
import com.drscbt.screencapture.CapturerSCS;
import com.drscbt.shared.utils.Utils;
import com.jakewharton.threetenabp.AndroidThreeTen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainActivity extends AppCompatActivity implements IReferrableActivity {
    private volatile static MainActivity _instance;
    private Logger _log = LoggerFactory.getLogger(MainActivity.class);
    private Spinner _automationDropdown;
    private static AutoListEntry _lastScriptUsed;
    private List<AutoListEntry> _autosList;
    private boolean _startBtnPurposeStart = true;
    private static int activityCreatedN;

    private List<String> _checks() {
        List<String> messages = new ArrayList<String>();

        List<String> permissions = new ArrayList<String>();
        permissions.add(CapturerSCDPGL.PERMISSION_REQUIRED);
        permissions.add(CapturerSCS.PERMISSION_REQUIRED);
        permissions.add("android.permission.WRITE_MEDIA_STORAGE");
        permissions.add(InjEvProviderPrivApiReflect.PERMISSION_REQUIRED);

        for (String permission : permissions) {
            if (!Perm.checkPerm(permission)) {
                String m = String.format("no %s permission", permission);
                messages.add(m);
                this._log.error(m);
            }
        }

        try {
            ConfLoader.getConf();
        } catch (Exception e) {
            messages.add(e.toString());
            this._log.error(e.getMessage(), e);
        }

        try {
            DevDBInfo.getInstance();
        } catch (Exception e) {
            messages.add(e.toString());
            this._log.error(e.getMessage(), e);
        }

        try {
            StaticConf.getInstance().checkConfAndDataDir();
        } catch (Exception e) {
            messages.add(e.toString());
            this._log.error(e.getMessage(), e);
        }

        try {
            StaticConf.getInstance().checkScriptsLocalDataDir();
        } catch (Exception e) {
            messages.add(e.toString());
            this._log.error(e.getMessage(), e);
        }

        try {
            StaticConf.getInstance().checkTessDirs();
        } catch (Exception e) {
            messages.add(e.toString());
            this._log.error(e.getMessage(), e);
        }

        try {
            StaticConf.getInstance().checkDumpScreenDir();
        } catch (Exception e) {
            messages.add(e.toString());
            this._log.error(e.getMessage(), e);
        }

        try {
            this._autosList = (new AutomationLoader()).listIds();
            if (this._autosList.isEmpty()) {
                messages.add("no automation loaded");
            }
        } catch (Exception e) {
            messages.add(e.toString());
            this._log.error(e.getMessage(), e);
        }

        return messages;
    }

    private void _appStartBeforeChecks() {
        this._log.info(Utils.repeat("*", 50));
        AndroidThreeTen.init(this);

        this._log.info("drscbt {}", this._versionString());
        AndroidUtils.DispSize dispSize = AndroidUtils.getDispSize();
        AndroidUtils.DispSize realDispSize = AndroidUtils.getRealDispSize();
        this._log.debug("dispSize={}x{} realDispSize={}x{} {}",
            dispSize.w, dispSize.h,
            realDispSize.w, realDispSize.h,
            AndroidUtils.hasSoftwareKeys() ? "soft keys" : ""
        );
    }

    private String _versionString() {
        return AndroidUtils.versionName();
    }

    protected void onCreate(Bundle state) {
        super.onCreate(state);
        MainActivity.activityCreatedN++;
        setContentView(R.layout.activity_main);

        if (MainActivity.activityCreatedN == 1) {
            this._appStartBeforeChecks();
        }

        List<String> emsgs = this._checks();
        if (emsgs.size() == 0) {
            this._initFormWhenCanStart();
            if (MainActivity.activityCreatedN == 1) {
                this._autostart();
            }
        } else {
            this._initFormWhenCannotStart(emsgs);
        }
    }

    private void _initFormWhenCanStart() {
        TextView statusLbl = this.findViewById(R.id.status_lbl);
        statusLbl.setMovementMethod(new ScrollingMovementMethod());
        statusLbl.setHorizontallyScrolling(true);

        TextView verLbl = this.findViewById(R.id.version_lbl);
        verLbl.setText(this._versionString());

        this._populateAutomationDropdown();
    }

    private void _autostart() {
        Conf c = ConfLoader.getConf();
        if (c.autostart.enabled) {
            String scriptName = c.autostart.script;
            AutoListEntry found = this._autosList.stream()
                .filter(e -> e.name.equals(scriptName))
                .findFirst().orElse(null);
            if (found == null) {
                String loaded = this._autosList.stream().map(x -> x.toString()).collect(Collectors.joining(";"));
                this._log.error("no {} automation available to autostart. ({})", scriptName, loaded);
            } else {
                this._log.info("autostarting {}", scriptName);
                if (this._automationDropdown != null) {
                    this._setDropdownValue(found);
                }
                this._startAutomation(found);
            }
        }
    }

    private void _initFormWhenCannotStart(List<String> messages) {
        Intent intent = new Intent(this, StartupCheckErrActivity.class);
        intent.putExtra(StartupCheckErrActivity.ERRORS_KEY, messages.toArray(new String[0]));
        this.startActivity(intent);
        this.finish();
    }

    private void _populateAutomationDropdown() {
        this._automationDropdown = findViewById(R.id.automation_dropdown);

        List<AutoListEntry> autosToList;
        if (ConfLoader.getConf().includeServiceScripts) {
            autosToList = this._autosList;
        } else {
            autosToList = this._autosList.stream()
                .filter(a -> a.purpose == AutoListEntry.APurpose.ACT_WRK)
                .collect(Collectors.toList());
        }

        if (autosToList.size() == 1) {
            ((ViewGroup)this._automationDropdown.getParent()).removeView(this._automationDropdown);
            this._automationDropdown = null;

            ConstraintLayout layout = this.findViewById(R.id.main_act_c_layout);
            ConstraintSet set = new ConstraintSet();
            set.clone(layout);
            TextView statusLbl = this.findViewById(R.id.status_lbl);
            Button startBtn = this.findViewById(R.id.start_btn);
            set.connect(statusLbl.getId(), ConstraintSet.TOP, startBtn.getId(), ConstraintSet.BOTTOM, 16);
            set.applyTo(layout);

            return;
        }

        AutoListEntry[] ids = autosToList.toArray(new AutoListEntry[0]);

        ArrayAdapter<AutoListEntry> custAdapter
            = new ArrayAdapter<AutoListEntry>(
            this, android.R.layout.simple_spinner_dropdown_item, ids) {
            public View getView(int position, View convertView, ViewGroup parent) {
                return this._getView(position, convertView, parent);
            }
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return this._getView(position, convertView, parent);
            }
            private View _getView(int position, View convertView, ViewGroup parent) {
                View itemView = super.getView(position, convertView, parent);
                TextView tv1 = itemView.findViewById(android.R.id.text1);
                tv1.setText(this.getItem(position).name);
                return itemView;
            }
        };
        this._automationDropdown.setAdapter(custAdapter);

        if (MainActivity._lastScriptUsed != null) {
            this._setDropdownValue(MainActivity._lastScriptUsed);
        }
    }

    private void _setDropdownValue(AutoListEntry e) {
        if ((!ConfLoader.getConf().includeServiceScripts)
            && (e.purpose == AutoListEntry.APurpose.SERVICE)) {
            return;
        }
        ArrayAdapter<AutoListEntry> adapter;
        adapter = (ArrayAdapter<AutoListEntry>) this._automationDropdown.getAdapter();
        int pos = adapter.getPosition(e);
        this._automationDropdown.setSelection(pos);
    }

    public void onBtnClickHandler(View view) {
        if (this._startBtnPurposeStart) {
            AutoListEntry ae;
            if (this._automationDropdown != null) {
                ae = (AutoListEntry) this._automationDropdown.getSelectedItem();
            } else {
                ae = this._autosList.get(0);
            }
            this._log.info("start button. {}", ae.name);
            this._startAutomation(ae);
        } else {
            this._stopAutomation();
        }
    }

    private void _stopAutomation() {
        DrscbtService srv = DrscbtService.instance;
        srv.stopBot();
    }

    public void _startAutomation(AutoListEntry entry) {
        Intent intent = new Intent(this, DrscbtService.class);
        intent.putExtra(DrscbtService.AUTOMATION_NAME_KEY, entry);
        MainActivity._lastScriptUsed = entry;
        this.startService(intent);
    }

    public void exitBtnClickHandler(View view) {
        Class scls = DrscbtService.class;
        Intent intent = new Intent(this, scls);
        this._log.debug("terminating {} service", scls);
        this.stopService(intent);

        while(AndroidUtils.serviceRunning(scls)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // swallow
            }
        }
        this._log.debug("{} stopped", scls);

        AndroidUtils.closeActNExit(this);
    }

    public void setReference(Activity a) {
        MainActivity._instance = (MainActivity) a;
        this._log.trace("activity reference is restored " +
            "(prsumably brought to foreground) ref={} t={}",
            a, Thread.currentThread().getId());
    }

    public void nullReference() {
        MainActivity._instance = null;
        this._log.trace("activity reference set to null " +
            "(prsumably it's minimized). t={}",
            Thread.currentThread().getId());
    }

    static public MainActivity getInstance() {
        return MainActivity._instance;
    }

    public void stateNeedsUpdate() {
        this.runOnUiThread(this::_updateStateInUI);
    }

    private void _updStartButton(boolean purposeIsToStart) {
        this._startBtnPurposeStart = purposeIsToStart;
        TextView btn = this.findViewById(R.id.start_btn);
        btn.setText(purposeIsToStart ? "start" : "stop");
    }

    private StringBuilder _getStackTraces(List<Exception> excps) {
        StringBuilder sb = new StringBuilder();
        if (excps != null) {
            for (Exception e : excps) {
                sb.append("---\n");
                String trace = Utils.getExcpText(e);
                sb.append(trace);
                if (!trace.endsWith("\n")) {
                    sb.append("\n");
                }
            }
        }
        return sb;
    }

    private void _updateStateInUI() {
        TextView statusLbl = this.findViewById(R.id.status_lbl);
        DrscbtService srv = DrscbtService.instance;
        if (srv == null) {
            statusLbl.setText("");
            this._updStartButton(true);
        } else {
            this._updStartButton(!srv.botRunning);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("botRunning=%b\n", srv.botRunning));
            if (!srv.botRunning) {
                sb.append(String.format("completionStatus=%s\n", srv.completionStatus));
                sb.append(String.format("lastShutdownClean=%b\n", srv.lastShutdownClean));
                if (srv.completionStatus != DrscbtService.CompletionStatus.INTERRUPTED) {
                    sb.append(this._getStackTraces(srv.lastExceptions));
                }
                if (!srv.lastShutdownClean) {
                    Button btn = this.findViewById(R.id.start_btn);
                    btn.setEnabled(false);
                }
            }
            statusLbl.setText(sb);
        }
    }

    protected void onResume() {
        this._updateStateInUI();
        super.onResume();
    }
}
