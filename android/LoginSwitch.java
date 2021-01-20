package com.drscbt.scripts.inst;

import com.drscbt.auto_iface.ILevenshtein;
import com.drscbt.auto_iface.IInteractor;
import com.drscbt.auto_iface.AutoExcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class LoginSwitch {
    private IInteractor _inter;
    private Iterator<String> _loginAccsIter;
    private int _loginAccsSwitchNum;
    private int _loginAccsNum;
    private String _activeAccName;
    private Logger _log = LoggerFactory.getLogger(LoginSwitch.class);
    static final int LOGIN_SWITCH_OCR_MAX_STR_DIST = 2;

    LoginSwitch(IInteractor inter) {
        this._inter = inter;
    }

    public void init() throws InterruptedException {
        this._goOwnProfile();
        ProfileScreen ps = (new ProfileScreen(this._inter));

        ps.waitEnsureLandedAndFound(true);

        this._activeAccName = ps.getHeaderName();

        this._log.info("startup account from profile header \"{}\"", this._activeAccName);

        LoginSwitchList.AvailLoginAccs availLoginAccs = this._getAvailLoginAccs();

        this._log.info("startup account from login switch menu \"{}\"", availLoginAccs.active);

        this._checkHeaderName(availLoginAccs.active, this._activeAccName);

        this._loginAccsIter = availLoginAccs.allNames.iterator();
        this._loginAccsNum = availLoginAccs.allNames.size();
    }

    boolean switchLogin() throws InterruptedException {
        String switchingTo = this._nextLoginAccount();
        if (switchingTo == null) {
            this._log.info("no more login accounts");
            return false;
        }
        this._loginAccsSwitchNum++;

        this._log.info("login switch from \"{}\" to \"{}\" {}/{}",
            this._activeAccName, switchingTo, this._loginAccsSwitchNum, this._loginAccsNum);

        this._switchLogin(switchingTo);
        this._activeAccName = switchingTo;

        return true;
    }

    private String _nextLoginAccount() {
        if (this._loginAccsIter.hasNext()) {
            return this._loginAccsIter.next();
        }
        return null;
    }

    private LoginSwitchList.AvailLoginAccs _getAvailLoginAccs() throws InterruptedException {
        LoginSwitchList lsl = new LoginSwitchList(this._inter);
        this._goLoginSwitchList(lsl);
        lsl.waitEnsureOpen();
        this._inter.pause(2 * 1000);

        LoginSwitchList.AvailLoginAccs availLoginAccs = lsl.getAvailableLoginAccounts();

        String availLoginAccsallNamesStr
            = enumerate(availLoginAccs.allNames, 1)
            .stream()
            .map((e) -> String.format("%d.\"%s\"", e.i, e.e))
            .collect(Collectors.joining(", "));
        this._log.info("availLoginAccs.allNames: {}", availLoginAccsallNamesStr);

        return availLoginAccs;
    }

    private void _switchLogin(String switchToAccount) throws InterruptedException {

        LoginSwitchList lsl = new LoginSwitchList(this._inter);

        ILevenshtein.ILevenshteinComparisonResult close
            = this._inter.levenshtein().close(switchToAccount, this._activeAccName, LOGIN_SWITCH_OCR_MAX_STR_DIST);

        if (close.close()) {
            if (!close.exact()) {
                this._log.warn(close.toString());
            }
            this._log.info("\"{}\" already active", switchToAccount);
            this._inter.typer().pressBack();
            lsl.waitEnsureGone();
            return;
        }

        ProfileScreen ps = (new ProfileScreen(this._inter));

        boolean switchListAlreadyOpen = this._loginAccsSwitchNum == 1;
        if (!switchListAlreadyOpen) {
            MainScreen m = new MainScreen(this._inter);
            m.runAppFromMainScreen();
            m.waitEnsureLanded();
            this._goOwnProfile();
            ps.waitEnsureLandedAndFound(false);

            this._goLoginSwitchList(lsl);
            lsl.waitEnsureOpen();
            this._inter.pause(2 * 1000);
        }

        lsl.tapListItem(switchToAccount);

        ps.waitEnsureLandedAndFound(true);

        this._checkHeaderName(switchToAccount, ps.getHeaderName());
    }

    private void _checkHeaderName(String switchToAccount, String profileHeaderText) throws InterruptedException {
        ILevenshtein.ILevenshteinComparisonResult close = this._inter.levenshtein().close(switchToAccount, profileHeaderText, LOGIN_SWITCH_OCR_MAX_STR_DIST);
        if (close.close()) {
            if (!close.exact()) {
                this._log.warn(close.toString());
            }
        } else {
            String m = String.format("login swithch error. " +
                    "expectedAccountName=\"%s\" profileHeaderText=\"%s\" %s",
                switchToAccount, profileHeaderText, close.toString());
            this._log.error(m);
            throw new AutoExcp(m);
        }
    }

    private void _goLoginSwitchList(LoginSwitchList lsl) throws InterruptedException {
        this._inter.currScrPicProvider().getCurrScrPic().update();
        if (lsl.open()) {
            return;
        }
        new BottomNavBar(this._inter).tapBottomNavbarLastBtn(1 * 1000);
    }

    private void _goOwnProfile() throws InterruptedException {
        new BottomNavBar(this._inter).tapBottomNavbarLastBtn(100);
        ProfileScreen ps = (new ProfileScreen(this._inter));
        ps.waitEnsureLandedAndFound(false);
        this._inter.pause(1000);
        new BottomNavBar(this._inter).tapBottomNavbarLastBtn(100);
    }

    String getActiveAccName() {
        return this._activeAccName;
    }

    private static <T> List<EnumeratedEntry<T>> enumerate(List<T> list, int startIdx) {
        List<EnumeratedEntry<T>> result = new ArrayList<EnumeratedEntry<T>>();
        for (int i = 0; i < list.size(); i++) {
            result.add(new EnumeratedEntry<T>(startIdx + i, list.get(i)));
        }
        return result;
    }

    static class EnumeratedEntry<T> {
        int i;
        T e;

        EnumeratedEntry(int i, T e) {
            this.i = i;
            this.e = e;
        }
    }
}
