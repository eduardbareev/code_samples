package com.drscbt.shared.piclocate.split;

import com.drscbt.shared.assetloader.ApkJarAssetLoaderProv;
import com.drscbt.shared.assetloader.IApkJarAssetLoader;
import com.drscbt.shared.piclib.ImgTools;
import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclib.PicOps;
import com.drscbt.shared.piclib.PlatformImgToolsExtProv;
import com.drscbt.shared.piclocate.MaskConf;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class TrivialSplitTest {
    @Test
    public void case1() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 1));
        exp.add(new Segment1D(5, 2));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf maskConf = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", maskConf, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case1DelimThickness2() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 4));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0xFFFFFF00, 15, 2, 0, 1, 0
        );
    }

    @Test
    public void case1DelimThickness3() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 9));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0xFFFFFF00, 15, 3, 0, 1, 0
        );
    }

    @Test
    public void case1DelimThickness4() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 16));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0xFFFFFF00, 15, 4, 0, 1, 0
        );
    }

    @Test
    public void case1nm() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(0, 1));
        exp.add(new Segment1D(2, 2));
        exp.add(new Segment1D(6, 3));
        exp.add(new Segment1D(12, 4));
        exp.add(new Segment1D(20, 5));
        MaskConf m = null;
        this._testSignleSplitAs1d(
            "split-vert-1nm.ppm", m, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case1SegmLenConstr(){
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        MaskConf maskConf = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", maskConf, exp, 0xFFFFFF00, 15, 1, 0, 3, 4
        );
    }

    @Test
    public void case2() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(6, 1));
        exp.add(new Segment1D(8, 2));
        exp.add(new Segment1D(12, 3));
        exp.add(new Segment1D(18, 4));
        exp.add(new Segment1D(26, 5));
        MaskConf m = new MaskConf(2, 3, 15, 31);
        this._testSignleSplitAs1d(
            "split-vert-2.ppm", m, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case2DelimThickness2() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(6, 4));
        exp.add(new Segment1D(12, 3));
        exp.add(new Segment1D(18, 4));
        exp.add(new Segment1D(26, 5));
        MaskConf m = new MaskConf(2, 3, 15, 31);
        this._testSignleSplitAs1d(
            "split-vert-2.ppm", m, exp, 0xFFFFFF00, 15, 2, 0, 1, 0
        );
    }

    @Test
    public void case2DelimThickness3() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(6, 9));
        exp.add(new Segment1D(18, 4));
        exp.add(new Segment1D(26, 5));
        MaskConf m = new MaskConf(2, 3, 15, 31);
        this._testSignleSplitAs1d(
            "split-vert-2.ppm", m, exp, 0xFFFFFF00, 15, 3, 0, 1, 0
        );
    }

    @Test
    public void case2DelimThickness4() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(6, 16));
        exp.add(new Segment1D(26, 5));
        MaskConf m = new MaskConf(2, 3, 15, 31);
        this._testSignleSplitAs1d(
            "split-vert-2.ppm", m, exp, 0xFFFFFF00, 15, 4, 0, 1, 0
        );
    }

    @Test
    public void case2nm() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 1));
        exp.add(new Segment1D(5, 2));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf m = null;
        this._testSignleSplitAs1d(
            "split-vert-2nm.ppm", m, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case3() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 1));
        exp.add(new Segment1D(5, 2));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        exp.add(new Segment1D(29, 1));
        MaskConf m = new MaskConf(2, 3, 15, 27);
        this._testSignleSplitAs1d(
            "split-vert-3.ppm", m, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case3DelimThickness2() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 4));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 7));
        MaskConf m = new MaskConf(2, 3, 15, 27);
        this._testSignleSplitAs1d(
            "split-vert-3.ppm", m, exp, 0xFFFFFF00, 15, 2, 0, 1, 0
        );
    }

    @Test
    public void case3DelimThickness3() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 9));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 7));
        MaskConf m = new MaskConf(2, 3, 15, 27);
        this._testSignleSplitAs1d(
            "split-vert-3.ppm", m, exp, 0xFFFFFF00, 15, 3, 0, 1, 0
        );
    }

    @Test
    public void case3DelimThickness4() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 16));
        exp.add(new Segment1D(23, 7));
        MaskConf m = new MaskConf(2, 3, 15, 27);
        this._testSignleSplitAs1d(
            "split-vert-3.ppm", m, exp, 0xFFFFFF00, 15, 4, 0, 1, 0
        );
    }

    @Test
    public void case3nm() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(0, 1));
        exp.add(new Segment1D(2, 2));
        exp.add(new Segment1D(6, 3));
        exp.add(new Segment1D(12, 4));
        exp.add(new Segment1D(20, 5));
        exp.add(new Segment1D(26, 1));
        MaskConf m = null;
        this._testSignleSplitAs1d(
            "split-vert-3nm.ppm", m, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case4() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 1));
        exp.add(new Segment1D(5, 2));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 26);
        this._testSignleSplitAs1d(
            "split-vert-4.ppm", m, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case4DelimThickness2() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 4));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 26);
        this._testSignleSplitAs1d(
            "split-vert-4.ppm", m, exp, 0xFFFFFF00, 15, 2, 0, 1, 0
        );
    }

    @Test
    public void case4DelimThickness3() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 9));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 26);
        this._testSignleSplitAs1d(
            "split-vert-4.ppm", m, exp, 0xFFFFFF00, 15, 3, 0, 1, 0
        );
    }

    @Test
    public void case4DelimThickness4() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 16));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 26);
        this._testSignleSplitAs1d(
            "split-vert-4.ppm", m, exp, 0xFFFFFF00, 15, 4, 0, 1, 0
        );
    }

    @Test
    public void case4NoMask() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(0, 1));
        exp.add(new Segment1D(2, 2));
        exp.add(new Segment1D(6, 3));
        exp.add(new Segment1D(12, 4));
        exp.add(new Segment1D(20, 5));
        MaskConf m = null;
        this._testSignleSplitAs1d(
            "split-vert-4nm.ppm", m, exp, 0xFFFFFF00, 15, 1, 0, 1, 0
        );
    }

    @Test
    public void case1ChanErrWholePicIsSegment() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 25));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0xFFFFFF00, 14, 1, 0, 1, 0
        );
    }

    @Test
    public void case1ChanErrWholePicIsBackground() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0x80808000, 128, 1, 0, 1, 0
        );
    }

    @Test
    public void realCase1() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(277 , 312));
        exp.add(new Segment1D(676 , 65 ));
        exp.add(new Segment1D(818 , 67 ));
        exp.add(new Segment1D(962 , 62 ));
        exp.add(new Segment1D(1106, 62 ));
        MaskConf m = new MaskConf(166, 248, 296, 928);
        this._testSignleSplitAs1d(
            "split-v-real-1.png", m, exp, 0xFFFFFF00, 0, 75, 0, 1, 0
        );
    }

    @Test
    public void case1Vert2d() {
        Set<Segment2D> exp = new HashSet<Segment2D>();
        exp.add(new Segment2D(2, 15, 3, 1));
        exp.add(new Segment2D(2, 15, 5, 2));
        exp.add(new Segment2D(2, 15, 9, 3));
        exp.add(new Segment2D(2, 15, 15, 4));
        exp.add(new Segment2D(2, 15, 23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSingleSplitAs2d(
            "split-vert-1.ppm", m, exp,
            0xFFFFFF00, 15, 1, 0,
            TrivialSplitter.Axis.V,
            1, 0
        );
    }

    @Test
    public void case1Hor2d() {
        Set<Segment2D> exp = new HashSet<Segment2D>();
        exp.add(new Segment2D(3, 1,3, 15));
        exp.add(new Segment2D(5, 2,3, 15));
        exp.add(new Segment2D(9, 3,3, 15));
        exp.add(new Segment2D(15, 4,3, 15));
        exp.add(new Segment2D(23, 5,3, 15));
        MaskConf m = new MaskConf(3, 3,25, 15);
        this._testSingleSplitAs2d(
            "split-hor-1.ppm", m, exp,
            0xFFFFFF00, 15, 1, 0,
            TrivialSplitter.Axis.H,
            1, 0
        );
    }

    @Test
    public void case1NoMaskVert2d() {
        Set<Segment2D> exp = new HashSet<Segment2D>();
        exp.add(new Segment2D(0, 15, 0, 1));
        exp.add(new Segment2D(0, 15, 2, 2));
        exp.add(new Segment2D(0, 15, 6, 3));
        exp.add(new Segment2D(0, 15, 12, 4));
        exp.add(new Segment2D(0, 15, 20, 5));
        MaskConf m = null;
        this._testSingleSplitAs2d(
            "split-vert-1nm.ppm", m, exp,
            0xFFFFFF00, 15, 1, 0,
            TrivialSplitter.Axis.V,
            1, 0
        );
    }

    @Test
    public void case1NoMaskHor2d() {
        Set<Segment2D> exp = new HashSet<Segment2D>();
        exp.add(new Segment2D(0, 1, 0, 15));
        exp.add(new Segment2D(2, 2, 0, 15));
        exp.add(new Segment2D(6, 3, 0, 15));
        exp.add(new Segment2D(12, 4, 0, 15));
        exp.add(new Segment2D(20, 5, 0, 15));
        MaskConf m = null;
        this._testSingleSplitAs2d(
            "split-hor-1nm.ppm", m, exp,
            0xFFFFFF00, 15, 1, 0,
            TrivialSplitter.Axis.H,
            1, 0
        );
    }

    @Test
    public void doubleSplitVThenH() {
        Set<Segment2D> exp = new HashSet<Segment2D>();
        exp.add(new Segment2D(2, 1, 3, 1));
        exp.add(new Segment2D(4, 2, 5, 2));
        exp.add(new Segment2D(14, 3, 5, 2));
        exp.add(new Segment2D(3, 4, 9, 3));
        exp.add(new Segment2D(10, 5, 9, 3));

        MaskConf m = new MaskConf(2, 3, 15, 9);
        this._testDoubleSplitAs2d(
            "split-double.ppm", m, exp,
            0xFFFFFF00, 15, 1,
            0,
            TrivialSplitter.Axis.V, TrivialSplitter.Axis.H,
            1, 0,
            1, 0
        );
    }

    @Test
    public void doubleSplitVThenHSegmLengthConstraint() {
        Set<Segment2D> exp = new HashSet<Segment2D>();
        exp.add(new Segment2D(14, 3, 5, 2));
        MaskConf m = new MaskConf(2, 3, 15, 9);
        this._testDoubleSplitAs2d(
            "split-double.ppm", m, exp,
            0xFFFFFF00, 15, 1, 0,
            TrivialSplitter.Axis.V, TrivialSplitter.Axis.H,
            2, 2,
            3, 3
        );
    }

    @Test
    public void realCasedoubleSplitVThenHSegmLengthConstraint() {
        Set<Segment2D> exp = new HashSet<Segment2D>();
        exp.add(new Segment2D(0, 238, 243, 238));
        exp.add(new Segment2D(241, 238, 243, 238));
        exp.add(new Segment2D(482, 238, 243, 238));

        exp.add(new Segment2D(0, 238, 484, 238));
        exp.add(new Segment2D(241, 238, 484, 238));
        exp.add(new Segment2D(482, 238, 484, 238));

        exp.add(new Segment2D(0, 238, 725, 238));
        exp.add(new Segment2D(241, 238, 725, 238));
        exp.add(new Segment2D(482, 238, 725, 238));

        exp.add(new Segment2D(0, 238, 966, 238));
        exp.add(new Segment2D(241, 238, 966, 238));
        exp.add(new Segment2D(482, 238, 966, 238));
        exp.add(new Segment2D(482, 238, 966, 238));

        MaskConf m = null;
        this._testDoubleSplitAs2d(
            "split-double-real.png",
            m, exp, 0xFFFFFF00, 15,
            1, 0,
            TrivialSplitter.Axis.V, TrivialSplitter.Axis.H,
            238, 238,
            238, 238
        );
    }

    @Test
    public void case1DelimThickness2RequireBothGaps() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0xFFFFFF00, 15, 2,
            ITrivialSplitter.LEAD | ITrivialSplitter.TRAIL, 1, 0
        );
    }

    @Test
    public void case1DelimThickness2RequireLeadGap() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        exp.add(new Segment1D(23, 5));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0xFFFFFF00, 15, 2,
            ITrivialSplitter.LEAD, 1, 0
        );
    }

    @Test
    public void case1DelimThickness2RequireTrailGap() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(3, 4));
        exp.add(new Segment1D(9, 3));
        exp.add(new Segment1D(15, 4));
        MaskConf m = new MaskConf(2, 3, 15, 25);
        this._testSignleSplitAs1d(
            "split-vert-1.ppm", m, exp, 0xFFFFFF00, 15, 2,
            ITrivialSplitter.TRAIL, 1, 0
        );
    }

    @Test
    public void realCase1RequireBothGaps() {
        Set<Segment1D> exp = new HashSet<Segment1D>();
        exp.add(new Segment1D(676, 65));
        exp.add(new Segment1D(818, 67));
        exp.add(new Segment1D(962, 62));
        MaskConf m = new MaskConf(166, 248, 296, 928);
        this._testSignleSplitAs1d(
            "split-v-real-1.png", m, exp, 0xFFFFFF00, 0, 75,
            ITrivialSplitter.TRAIL | ITrivialSplitter.LEAD,
            1, 0
        );
    }

    private void _testSignleSplitAs1d(
        String filename, MaskConf mc,
        Set<Segment1D> exp, int bgRgba,
        int channelErr, int delimiterThickness,
        int edgeGapStrictnessMode, int sLenMin, int sLenMax
    ) {
        PicData pic = this._loadPicResource(filename);
        TrivialSplitter ts = new TrivialSplitter();
        Set<Segment1D> act = ts.split(
            pic, mc, bgRgba, channelErr,
            delimiterThickness, edgeGapStrictnessMode,
            TrivialSplitter.Axis.V, sLenMin, sLenMax
        );
        Assert.assertEquals(exp, act);

        if (mc != null) {
            mc.CCW_90(pic.width);
        }

        PicData rotated = PicOps.copyCCW90(pic);
        Set<Segment1D> result = ts.split(rotated, mc, bgRgba, channelErr,
                delimiterThickness, edgeGapStrictnessMode,
                TrivialSplitter.Axis.H, sLenMin, sLenMax);
        Assert.assertEquals(exp, result);
    }

    private void _testSingleSplitAs2d(
        String filename, MaskConf mc,
        Set<Segment2D> exp, int bgRgba,
        int channelErr, int delimiterThickness,
        int edgeGapThickness, TrivialSplitter.Axis axis, int sLenMin, int sLenMax
    ) {
        PicData pic = this._loadPicResource(filename);
        TrivialSplitter ts = new TrivialSplitter();
        Set<Segment2D> act = ts.splitAs2d(
            pic, mc, bgRgba, channelErr,
            delimiterThickness, edgeGapThickness, axis,
            sLenMin, sLenMax
        );
        Assert.assertEquals(exp, act);
    }

    private void _testDoubleSplitAs2d(
        String filename, MaskConf mc,
        Set<Segment2D> exp, int bgRgba,
        int channelErr, int delimiterThickness,
        int edgeGapThickness,
        TrivialSplitter.Axis firstAxis, TrivialSplitter.Axis secondAxis,
        int firstPassSegmentLenMin, int firstPassSegmentLenMax,
        int secondPassSegmentLenMin, int secondPassSegmentLenMax
    ) {
        PicData pic = this._loadPicResource(filename);
        TrivialSplitter ts = new TrivialSplitter();
        Set<Segment2D> act = ts.doubleSplit(pic, mc, bgRgba, channelErr,
            delimiterThickness, edgeGapThickness, firstAxis, secondAxis,
            firstPassSegmentLenMin, firstPassSegmentLenMax,
            secondPassSegmentLenMin, secondPassSegmentLenMax);
        Assert.assertEquals(exp, act);
    }

    private PicData _loadPicResource(String name) {
        String picName = String.format("match_test_pics/%s", name);
        IApkJarAssetLoader ldr = ApkJarAssetLoaderProv.getLoader();
        return this._imgTools.fromExt(ldr.load(picName), picName);
    }

    private ImgTools _imgTools;

    public TrivialSplitTest() {
        this._imgTools = PlatformImgToolsExtProv.getTools();
    }
}
