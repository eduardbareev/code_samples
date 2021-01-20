package com.drscbt.shared.piclocate;

import com.drscbt.shared.assetloader.ApkJarAssetLoaderProv;
import com.drscbt.shared.color.Compare;
import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclocate.srchpattern.PicLoader;
import com.drscbt.shared.piclocate.twodmatcher.ITwoDMatcher;
import com.drscbt.shared.piclocate.twodmatcher.RK2DCrossNa;
import com.drscbt.shared.piclocate.twodmatcher.TwoDBasicSumCrossNa;
import com.drscbt.shared.utils.Measure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.*;

public class CombinedPicMatchingTest {
    private PicLoader _picsLoader;
    private static final int FUZZY_ERR = 12;
    private Logger _log = LoggerFactory.getLogger(CombinedPicMatchingTest.class);

    @Test
    public void exactMatches() {
        List<ITwoDMatcher> impls = new LinkedList<>();
        impls.add(new RK2DCrossNa());

        List<TestDefinition> testSet = this._getCleanTestsDefinitions();
        for (ITwoDMatcher matcherImplementation : impls) {
            for (TestDefinition testDefinition : testSet) {
                this._performTestForPatternAndCanvas(testDefinition, matcherImplementation);
            }
        }
    }

    @Test
    public void fuzzyMatches() {
        List<ITwoDMatcher> matcherImpls = new LinkedList<>();
        matcherImpls.add(new TwoDBasicSumCrossNa(FUZZY_ERR));

        List<TestDefinition> testDefs = _getFuzzyTestDefinitions();
        for (ITwoDMatcher matcherImpl : matcherImpls) {
            for (TestDefinition testDefinition : testDefs) {
                this._performTestForPatternAndCanvas(testDefinition, matcherImpl);
            }
        }
    }

    private TestDefinition _getFuzzyMicroPatTestDef() {
        Set<Point> microPatExpPnts = new HashSet<Point>(Arrays.asList(
            new Point( 3, 2 ),
            new Point( 3, 9 ),
            new Point( 3, 16),
            new Point( 3, 23),
            new Point( 3, 30),
            new Point( 3, 37),
            new Point( 3, 44),
            new Point( 3, 51),
            new Point( 3, 58),
            new Point( 3, 65),
            new Point( 3, 72),
            new Point( 3, 79),
            new Point( 3, 86),
            new Point(13, 9 ),
            new Point(13, 16),
            new Point(13, 23),
            new Point(13, 30),
            new Point(13, 37),
            new Point(13, 44),
            new Point(13, 51),
            new Point(13, 58),
            new Point(13, 65),
            new Point(13, 72),
            new Point(13, 79),
            new Point(13, 86)
        ));

        return new TestDefinition(
            "micro_canvas_o25_d12.ppm",
            "micro_pat.ppm",
            microPatExpPnts
        );
    }

    private List<TestDefinition> _getFuzzyArtNoiseTestDefs() {
        List<TestDefinition> fuzzyArtNoiseTestDefs = new LinkedList<TestDefinition>();
        for (TestDefinition testDef : this._getCleanTestsDefinitions()) {
            testDef.noiseErr = 5;
            fuzzyArtNoiseTestDefs.add(testDef);
        }
        return fuzzyArtNoiseTestDefs;
    }

    private List<TestDefinition> _getFuzzyRealNoiseTestDefs1() {
        List<TestDefinition> realNoiseTestDefs = new LinkedList<>();
        realNoiseTestDefs.add(new TestDefinition("andr-noise-canvas-1.png", "andr-noise-pattern.png", _locSet(new Point(94, 83))));
        realNoiseTestDefs.add(new TestDefinition("andr-noise-canvas-2.png", "andr-noise-pattern.png", _locSet(new Point(94, 83))));
        realNoiseTestDefs.add(new TestDefinition("andr-noise-canvas-3.png", "andr-noise-pattern.png", _locSet(new Point(94, 83))));

        realNoiseTestDefs.add(
            new TestDefinition("noise-checkboxes.png", "checkbox_1.png",
            _locSet(
                new Point(94, 582),
                new Point(269, 503),
                new Point(394, 731),
                new Point(393, 897),
                new Point(15, 380),
                new Point(145, 442),
                new Point(309, 824),
                new Point(354, 571),
                new Point(474, 483),
                new Point(124, 755)
            )));
        return realNoiseTestDefs;
    }

    private List<TestDefinition> _getFuzzyRealNoiseTestDefs2() {
        List<TestDefinition> realNoiseTestDefs = new LinkedList<>();
        int[] screenShiftOffsets = new int[]{0, -19, -25, -34};
        for (int i = 0; i < screenShiftOffsets.length; i++) {
            String screenFilename = String.format("screen-1-noise-%d.png", i + 1);
            realNoiseTestDefs.addAll(Arrays.asList(
                new TestDefinition(screenFilename, "btn-2.png", _locSet(new Point(629, 278 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "btn-1.png", _locSet(new Point(258, 282 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "icon-1.png", _locSet(new Point(82, 951 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "icon-2.png", _locSet(new Point(325, 948 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "icon-3.png", _locSet(new Point(561, 950 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "icon-4.png", _locSet(new Point(177, 412 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "text-1.png", _locSet(new Point(413, 237 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "text-2.png", _locSet(new Point(556, 237 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "text-3.png", _locSet(new Point(299, 242 + screenShiftOffsets[i]))),
                new TestDefinition(screenFilename, "lines-1.png", _locSet(new Point(297, 825 + screenShiftOffsets[i])))
            ));
        }
        return realNoiseTestDefs;
    }

    private List<TestDefinition> _getFuzzyRealNoiseTestDefs3() {
        List<TestDefinition> realNoiseTestDefs = new LinkedList<>();
        Set<Point> screen2PatternLocations = _locSet(
            new Point(464, 325),
            new Point(464, 469),
            new Point(464, 613),
            new Point(464, 757),
            new Point(464, 901),
            new Point(464, 1045)
        );
        int[] screenShiftOffsets = new int[]{0, -30, -49,};
        for (int i = 0; i < screenShiftOffsets.length; i++) {
            String screenFname = String.format("screen-2-noise-%d.png", i + 1);
            realNoiseTestDefs.add(
                    new TestDefinition(screenFname, "screen-2-btn-1.png", _mvLoc(screen2PatternLocations, screenShiftOffsets[i]))
            );
        }
        return realNoiseTestDefs;
    }

    private List<TestDefinition> _getFuzzyRealNoiseTestDefs4() {
        List<TestDefinition> realNoiseTestDefs = new LinkedList<>();
        int[] screenShiftOffsets = new int[]{0, -9, -27};
        for (int i = 0; i < screenShiftOffsets.length; i++) {
            String screenFname = String.format("screen-3-noise-%d.png", i + 1);
            realNoiseTestDefs.addAll(Arrays.asList(
                new TestDefinition(screenFname, "screen-3-icon-love-1.png", _locSet(new Point(22, 699 + screenShiftOffsets[i]))),
                new TestDefinition(screenFname, "screen-3-icon-love-2.png", _locSet(new Point(474, 1203))) //sticky
            ));
        }
        return realNoiseTestDefs;
    }

    private List<TestDefinition> _getFuzzyRealNoiseTestDefs() {
        List<TestDefinition> realNoiseTestDefs = new LinkedList<>();
        realNoiseTestDefs.addAll(this._getFuzzyRealNoiseTestDefs4());
        realNoiseTestDefs.addAll(this._getFuzzyRealNoiseTestDefs3());
        realNoiseTestDefs.addAll(this._getFuzzyRealNoiseTestDefs2());
        realNoiseTestDefs.addAll(this._getFuzzyRealNoiseTestDefs4());
        return realNoiseTestDefs;
    }

    private List<TestDefinition> _getCleanTestsDefinitions() {
        List<TestDefinition> pitPatPairs = new LinkedList<TestDefinition>();
        pitPatPairs.add(new TestDefinition("micro_canvas.ppm", "micro_pat.ppm", _locSet(new Point(3, 2))));
        pitPatPairs.add(new TestDefinition("backtrack_worst_canvas.png", "backtrack_worst_block.png", _locSet(new Point(1051, 804))));
        pitPatPairs.add(new TestDefinition("noisy_canvas.png", "noise_green.png", _locSet(new Point(1008, 795))));
        pitPatPairs.add(new TestDefinition("noisy_canvas.png", "noise_orange.png", _locSet(new Point(1045, 124))));
        pitPatPairs.add(new TestDefinition("noisy_canvas.png", "noise_red.png", _locSet(new Point(466, 430))));
        return pitPatPairs;
    }

    private List<TestDefinition> _getFuzzyTestDefinitions() {
        List<TestDefinition> allTestDefinitions = new LinkedList<TestDefinition>();
        allTestDefinitions.addAll(this._getFuzzyRealNoiseTestDefs());
        allTestDefinitions.addAll(this._getFuzzyArtNoiseTestDefs());
        allTestDefinitions.add(this._getFuzzyMicroPatTestDef());
        return allTestDefinitions;
    }

    public CombinedPicMatchingTest() {
        this._picsLoader = new PicLoader(ApkJarAssetLoaderProv.getLoader(), "match_test_pics");
    }

    private Set<Point> _locSet(Point... loc) {
        return new HashSet<>(Arrays.asList(loc));
    }

    private Set<Point> _mvLoc(Set<Point> ps, int y) {
        Set<Point> psCopy = new HashSet<Point>();
        for (Point p : ps) {
            psCopy.add(new Point(p.x, p.y + y));
        }
        return psCopy;
    }

    private void _putNoise(PicData p, int err) {
        Compare.noise(p, err);
    }

    private void _performTestForPatternAndCanvas(TestDefinition tsd, ITwoDMatcher matcher) {
        PicData pic = this._picsLoader.loadPattern(tsd.picFilename).pic;

        if (tsd.noiseErr != 0) {
            this._putNoise(pic, tsd.noiseErr);
        }

        PicData pat = this._picsLoader.loadPattern(tsd.patFilename).pic;

        Set<Point> mtchs;

        Measure m = new Measure();
        mtchs = matcher.match(pic, pat);
        m.done();

        String assertMsg = String.format("%s/%s", tsd.picFilename, tsd.patFilename);
        assertEquals(assertMsg, tsd.location.size(), mtchs.size());
        assertEquals(assertMsg, tsd.location, mtchs);

        String msg = this._makeBenchmarkMsg(
            matcher.toString(),
            tsd.patFilename,
            tsd.picFilename,
            m.took(),
            matcher.getFalseMatchesCount()
        );
        this._log.debug(msg);
    }

    private String _makeBenchmarkMsg(String implName, String patternFile, String haystackFile, float duration, int coll){
        return String.format("bnchmrk %-22s %-22s %-18s %6.2f %d", haystackFile, patternFile, implName, duration, coll);
    }

    static class TestDefinition {
        String picFilename;
        String patFilename;
        Set<Point> location;
        int noiseErr = 0;
        TestDefinition(String pic, String pat, Set<Point> location) {
            this.patFilename = pat;
            this.picFilename = pic;
            this.location = location;
        }
    }
}
