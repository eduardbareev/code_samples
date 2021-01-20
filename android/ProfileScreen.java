package com.drscbt.scripts.inst;

import com.drscbt.auto_iface.Base;
import com.drscbt.auto_iface.IAreaAbsPx;
import com.drscbt.auto_iface.IInteractor;
import com.drscbt.auto_iface.IPatOccurrence;
import com.drscbt.auto_iface.IPlainPat;
import com.drscbt.auto_iface.IPoint;
import com.drscbt.auto_iface.IScroller;
import com.drscbt.auto_iface.ISplitter;
import com.drscbt.auto_iface.MvEdge;
import com.drscbt.auto_iface.AutoExcp;
import com.drscbt.auto_iface.Offset;
import com.drscbt.auto_iface.Unit;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class ProfileScreen {
    private IInteractor _inter;

    ProfileScreen(IInteractor inter) {
        this._inter = inter;
    }

    boolean landed(boolean strictTop) {
        if (this.atTheTop()) {
            return true;
        }

        if (!strictTop) {
            IPlainPat gridIconInactive = this._inter.patLoader().get("profile_page_grid_icon_inactive.ppm");
            IPlainPat gridIconActive = this._inter.patLoader().get("profile_page_grid_icon_active.ppm");
            return Stream.of(gridIconInactive, gridIconActive)
                .anyMatch(p -> !this._inter.locator().findSnThrow(p).isEmpty());
        }

        return false;
    }

    boolean atTheTop() {
        IPlainPat followersText = this._inter.patLoader().get("profile_page_followers_text.ppm");
        IPlainPat postsText = this._inter.patLoader().get("profile_page_posts_text.ppm");
        IPlainPat notFoundText = this._inter.patLoader().get("profile_page_user_not_found_text.ppm");
        return Stream.of(postsText, followersText, notFoundText)
            .anyMatch(p -> !this._inter.locator().findSnThrow(p).isEmpty());
    }

    void waitEnsureLandedAndFound(boolean strictTop) throws InterruptedException {
        this._inter.pollTillCondMetOrFail(() -> landed(strictTop), 15 * 1000, 3);
        IPlainPat notFound = this._inter.patLoader().get("profile_page_user_not_found_text.ppm");
        if (!this._inter.locator().find(notFound).isEmpty()) {
            throw new AutoExcp(String.format("profile page says \"user not found\""));
        }
    }

    void scrollTop() throws InterruptedException {
        IPlainPat gridIconInactive = this._inter.patLoader().get("profile_page_grid_icon_inactive.ppm");
        IPlainPat gridIconActive = this._inter.patLoader().get("profile_page_grid_icon_active.ppm");
        IPatOccurrence gridOcc = Stream.of(gridIconInactive, gridIconActive)
            .filter(p -> !this._inter.locator().findSnThrow(p).isEmpty())
            .findAny()
            .map(mpat -> this._inter.locator().findSnThrow(mpat).get(0))
            .get();
        IPoint scrollPoint = gridOcc.getArea().getCenter();

        this._inter.scroller().scroll(scrollPoint, -500, IScroller.Unit.PX, IScroller.Mode.TOUCH);
    }

    boolean isPrivate() {
        IPlainPat lock1 = this._inter.patLoader().get("profile_page_lock_1.ppm");
        IPlainPat lock2 = this._inter.patLoader().get("profile_page_lock_2.ppm");
        return Stream.of(lock1, lock2)
            .anyMatch(p -> !this._inter.locator().findSnThrow(p).isEmpty());
    }

    int getPostsCount() throws InterruptedException {
        return this._pageStatsNumber(this._inter.patLoader().get("profile_page_posts_text.ppm"));
    }

    String getHeaderName() throws InterruptedException {
        IPlainPat chevronPat = this._inter.patLoader().get("profile_page_hd_chevron.ppm");
        IPlainPat followTextPat = this._inter.patLoader().get("profile_page_hd_follow_text.ppm");
        IPlainPat leftArrowPat = this._inter.patLoader().get("profile_page_hd_left_arrow.ppm");
        IPlainPat menuIconPat = this._inter.patLoader().get("profile_page_hd_menu_icon.ppm");
        IPlainPat verifiedIconPat = this._inter.patLoader().get("profile_page_hd_verified_icon.ppm");
        IPlainPat vertDotsPat = this._inter.patLoader().get("profile_page_hd_vert_dots.ppm");

        IAreaAbsPx scope = this._inter.mkAreaRes(
            new Offset(0, Base.ORIGIN, Unit.PX),
            new Offset(0, Base.OPPOSITE, Unit.PX),
            new Offset(0.12, Base.ORIGIN, Unit.AXIS_LENGTH),
            new Offset(0, Base.ORIGIN, Unit.PX)
        );

        List<IPatOccurrence> chevronOccs = this._inter.locator().find(chevronPat, scope);
        List<IPatOccurrence> followTextOccs = this._inter.locator().find(followTextPat, scope);
        List<IPatOccurrence> leftArrowOccs = this._inter.locator().find(leftArrowPat, scope);
        List<IPatOccurrence> menuIconOccs = this._inter.locator().find(menuIconPat, scope);
        List<IPatOccurrence> verifiedIconOccs = this._inter.locator().find(verifiedIconPat, scope);
        List<IPatOccurrence> vertDotsOccs = this._inter.locator().find(vertDotsPat, scope);

        boolean chevron = !chevronOccs.isEmpty();
        boolean followText = !followTextOccs.isEmpty();
        boolean leftArrow = !leftArrowOccs.isEmpty();
        boolean menuIcon = !menuIconOccs.isEmpty();
        boolean verifiedIcon = !verifiedIconOccs.isEmpty();
        boolean vertDots = !vertDotsOccs.isEmpty();

        IAreaAbsPx textArea;
        if (chevron && !leftArrow) {
            IAreaAbsPx a = chevronOccs.get(0).getArea();
            a.moveEdge(MvEdge.RIGHT, -1, Unit.SUBJ_WIDTH);
            a.setEdge(MvEdge.LEFT, new Offset(0, Base.ORIGIN, Unit.PX));
            textArea = a;
        } else if (leftArrow && vertDots && !verifiedIcon && !chevron) {
            IAreaAbsPx arrArea = leftArrowOccs.get(0).getArea();
            IAreaAbsPx dotsArea = vertDotsOccs.get(0).getArea();
            arrArea.moveEdge(MvEdge.LEFT, 1, Unit.SUBJ_WIDTH);
            arrArea.setEdge(MvEdge.RIGHT, new Offset(dotsArea.getLeft(), Base.ORIGIN, Unit.PX));
            textArea = arrArea;
        } else if (leftArrow && followText && !verifiedIcon && !chevron) {
            IAreaAbsPx arrArea = leftArrowOccs.get(0).getArea();
            IAreaAbsPx followTextArea = followTextOccs.get(0).getArea();
            arrArea.moveEdge(MvEdge.LEFT, 1, Unit.SUBJ_WIDTH);
            arrArea.setEdge(MvEdge.RIGHT, new Offset(followTextArea.getLeft(), Base.ORIGIN, Unit.PX));
            textArea = arrArea;
        } else if (leftArrow && verifiedIcon && !chevron) {
            IAreaAbsPx arrArea = leftArrowOccs.get(0).getArea();
            arrArea.moveEdge(MvEdge.LEFT, 1, Unit.SUBJ_WIDTH);
            IAreaAbsPx verifiedArea = verifiedIconOccs.get(0).getArea();
            arrArea.moveEdge(MvEdge.LEFT, 1, Unit.SUBJ_WIDTH);
            arrArea.setEdge(MvEdge.RIGHT, new Offset(verifiedArea.getLeft(), Base.ORIGIN, Unit.PX));
            textArea = arrArea;
        } else {
            String patsFoundStr = String.format("chevron=%b followText=%b " +
                "leftArrow=%b menuIcon=%b verifiedIcon=%b vertDots=%b",
                chevron, followText, leftArrow, menuIcon, verifiedIcon, vertDots);
            throw new UnsupportedOperationException("not implemented. " + patsFoundStr);
        }

        return this._inter.ocr().recognize(InstRecognParams.USERNAME_PARAMS, textArea);
    }

    void tapPostsCounter() throws InterruptedException {
        IPlainPat postsCaption = this._inter.patLoader().get("profile_page_posts_text.ppm");
        IPatOccurrence patOccurrence = this._inter.locator().find(postsCaption).get(0);
        this._inter.clicker().tap(patOccurrence.getArea().getCenter());
    }

    private int _pageStatsNumber(IPlainPat captionPat) throws InterruptedException {
        IPatOccurrence patOccurrence = this._inter.locator().find(captionPat).get(0);
        IAreaAbsPx area = this._moveAreaFromCaptionToNumber(patOccurrence);
        String numericText = this._inter.ocr().recognize(InstRecognParams.COUNTER_PARAMS, area);
        try {
            return this.counterNumFromOCRText(numericText);
        } catch (IllegalArgumentException e) {
            this._inter.screenDumper().lastCaptureHighlight(area, "ocrerr");
            throw e;
        }
    }

    private IAreaAbsPx _moveAreaFromCaptionToNumber(IPatOccurrence captionOcc) {
        IAreaAbsPx a = captionOcc.getArea();
        a.moveEdge(MvEdge.TOP_N_BOTTOM, -1.65f, Unit.SUBJ_HEIGHT);
        a.setWidth(0.23, Unit.SCREEN_WIDTH);
        a.setHeight(1.8, Unit.SUBJ_HEIGHT);
        return a;
    }

    int getFollowersCount() throws InterruptedException {
        return this._pageStatsNumber(this._inter.patLoader().get("profile_page_followers_text.ppm"));
    }

    void goAccountFollowersList() throws InterruptedException {
        IPlainPat followersText = this._inter.patLoader().get("profile_page_followers_text.ppm");
        IAreaAbsPx lblArea = this._inter.locator().find(followersText).get(0).getArea();
        lblArea.moveEdge(MvEdge.TOP, -2.5f, Unit.SUBJ_HEIGHT);
        IPoint pnt = lblArea.getCenter();

        this._inter.pause(1000);
        this._inter.clicker().tap(pnt);
    }

    boolean photogridIsOnScreen() {
        IPlainPat photoGridIconActive = this._inter.patLoader().get("profile_page_grid_icon_active.ppm");

        IAreaAbsPx scope = this._inter.mkAreaRes(
            //device-dependent
            new Offset(0.08, Base.ORIGIN, Unit.AXIS_LENGTH),
            new Offset(1, Base.ORIGIN, Unit.AXIS_LENGTH),
            new Offset(0.25, Base.ORIGIN, Unit.AXIS_LENGTH),
            new Offset(0, Base.ORIGIN, Unit.AXIS_LENGTH)
        );

        List<IPatOccurrence> occurrences = this._inter.locator().findSnThrow(photoGridIconActive);
        if (occurrences.isEmpty()) {
            return false;
        }
        IAreaAbsPx occurrenceArea = occurrences.get(0).getArea();
        return scope.contains(occurrenceArea);
    }

    void waitEnsurePhotogridIsOnScreen() throws InterruptedException {
        this._inter.pollTillCondMetOrFail(this::photogridIsOnScreen, 7 * 1000, 3);
    }

    List<IAreaAbsPx> splitPhotoGrid() throws InterruptedException {
        int channelErr = 10;
        List<IAreaAbsPx> spl = this._inter.splitter().trivialDblSplit(
                null, 0xFFFFFF00,
                channelErr, 3,
                0,
                ISplitter.SplitAxis.V, ISplitter.SplitAxis.H,
                100, 238,
                100, 238
        );

        return spl;

    }

    static int counterNumFromOCRText(String textIn) {
        String text = textIn.toLowerCase();

        Pattern ptrn = Pattern.compile("^(\\d+([\\.\\,]\\d+)?)([km])?$");

        Matcher mtchr = ptrn.matcher(text);
        if (!mtchr.find()) {
            throw new IllegalArgumentException(String.format("can't extract numeric value from \"%s\" OCR text", textIn));
        }

        String valueChars = mtchr.group(1).replace(',', '.');
        String symbol = mtchr.group(3);

        int n;
        if (symbol != null) {
            double mul;
            switch (symbol) {
                case "k":
                    mul = 1000d;
                    break;
                case "m":
                    mul = 1_000_000d;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + symbol);
            }
            n = (int) (Double.parseDouble(valueChars) * mul);
        } else {
            n = Integer.parseInt(valueChars.replace(".", ""));
        }

        return n;
    }
}
