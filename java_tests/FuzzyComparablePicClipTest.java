package com.drscbt.shared.piclib;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class FuzzyComparablePicClipTest {
    @Test
    public void equalsReportsFuzzySimilarity() {
        PicGrayscale subject = this._mkPic(32, 8);
        PicGrayscale totalSimilarity = this._mkPic(32, 8);

        PicGrayscale close = this._mkPic(32, 8);
        close.data[255] -= FuzzyComparablePicClip.SIMILAR;

        PicGrayscale closeButNotEnough = this._mkPic(32, 8);
        closeButNotEnough.data[254] -= (1 + FuzzyComparablePicClip.SIMILAR);

        PicGrayscale differentSize = this._mkPic(31, 8);

        FuzzyComparablePicClip subjectWrap = new FuzzyComparablePicClip(subject);
        FuzzyComparablePicClip totalSimilarityWrap = new FuzzyComparablePicClip(totalSimilarity);
        FuzzyComparablePicClip closeWrap = new FuzzyComparablePicClip(close);
        FuzzyComparablePicClip closeButNotEnoughWrap = new FuzzyComparablePicClip(closeButNotEnough);
        FuzzyComparablePicClip differentSizeWrap = new FuzzyComparablePicClip(differentSize);

        assertEquals(subjectWrap, subjectWrap);
        assertEquals(subjectWrap, totalSimilarityWrap);
        assertEquals(totalSimilarityWrap, subjectWrap);

        assertEquals(subjectWrap, closeWrap);
        assertEquals(closeWrap, subjectWrap);

        assertNotEquals(subjectWrap, closeButNotEnoughWrap);
        assertNotEquals(closeButNotEnoughWrap, subjectWrap);

        assertNotEquals(subjectWrap, differentSizeWrap);
        assertNotEquals(differentSizeWrap, subjectWrap);
    }

    @Test
    public void hashcodeBoundToDimensions() {
        PicGrayscale a = this._mkPic(32, 8);
        PicGrayscale b = this._mkPic(31, 8);
        FuzzyComparablePicClip ac = new FuzzyComparablePicClip(a);
        FuzzyComparablePicClip bc = new FuzzyComparablePicClip(b);

        assertNotEquals(ac.hashCode(), bc.hashCode());
    }

    @Test
    public void worksWithMap() {
        PicGrayscale subject = this._mkPic(32, 8);
        PicGrayscale totalSimilarity = this._mkPic(32, 8);

        PicGrayscale close = this._mkPic(32, 8);
        close.data[255] -= FuzzyComparablePicClip.SIMILAR;

        PicGrayscale closeButNotEnough = this._mkPic(32, 8);
        closeButNotEnough.data[254] -= (1 + FuzzyComparablePicClip.SIMILAR);

        PicGrayscale differentSize = this._mkPic(31, 8);

        FuzzyComparablePicClip subjectWrap = new FuzzyComparablePicClip(subject);
        FuzzyComparablePicClip totalSimilarityWrap = new FuzzyComparablePicClip(totalSimilarity);
        FuzzyComparablePicClip closeWrap = new FuzzyComparablePicClip(close);
        FuzzyComparablePicClip closeButNotEnoughWrap = new FuzzyComparablePicClip(closeButNotEnough);
        FuzzyComparablePicClip differentSizeWrap = new FuzzyComparablePicClip(differentSize);

        Map<FuzzyComparablePicClip, Void> map = new HashMap<FuzzyComparablePicClip, Void>();
        map.put(subjectWrap, null);

        assertTrue(map.containsKey(totalSimilarityWrap));
        assertTrue(map.containsKey(closeWrap));

        assertFalse(map.containsKey(closeButNotEnoughWrap));
        assertFalse(map.containsKey(differentSizeWrap));
    }

    private PicGrayscale _mkPic(int w, int h) {
        PicGrayscale p = PicGrayscale.create(w, h);
        for (int i = 0; i < p.data.length; i++) {
            p.data[i] = (byte) i;
        }
        return p;
    }
}
