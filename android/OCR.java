package com.drscbt.interactor;

import com.drscbt.andrapp.conf.StaticConf;
import com.drscbt.auto_iface.IAreaAbsPx;
import com.drscbt.auto_iface.IAutoPicData;
import com.drscbt.auto_iface.IOCR;
import com.drscbt.auto_iface.RecognParams;
import com.drscbt.auto_iface.TesseractEngineMode;
import com.drscbt.shared.assetloader.ApkJarAssetLoaderProv;
import com.drscbt.shared.assetloader.IApkJarAssetLoader;
import com.drscbt.shared.color.ColorConv;
import com.drscbt.shared.piclib.FuzzyComparablePicClip;
import com.drscbt.shared.piclib.PicData;
import com.drscbt.shared.piclib.PicGrayscale;
import com.drscbt.shared.utils.Measure;
import com.drscbt.shared.utils.Utils;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class OCR implements IOCR {
    private Logger _log = LoggerFactory.getLogger(OCR.class);
    private Map<FuzzyComparablePicClip, String> _cache;
    private TessBaseAPI _tess;
    private CurrScrPicProvider _currScrPic;
    private final Map<String, String> _tessDefaultStrOpts;
    private final String _trainDataPathParent;
    private TesseractEngineMode _currentMode = null;
    private static final TesseractEngineMode STARTUP_MODE = TesseractEngineMode.LEGACY;

    OCR(CurrScrPicProvider currScreenPicProv) {
        this._tess = new TessBaseAPI();

        this._copyDataFiles();

        this._trainDataPathParent = StaticConf.getInstance().tessTrainFilesDirParent.toString();

        this._tessDefaultStrOpts = new HashMap<>();
        this._tessDefaultStrOpts.put("load_bigram_dawg", TessBaseAPI.VAR_FALSE);
        this._tessDefaultStrOpts.put("load_unambig_dawg", TessBaseAPI.VAR_FALSE);
        this._tessDefaultStrOpts.put("load_number_dawg", TessBaseAPI.VAR_FALSE);
        this._tessDefaultStrOpts.put("load_punc_dawg", TessBaseAPI.VAR_FALSE);
        this._tessDefaultStrOpts.put("load_freq_dawg", TessBaseAPI.VAR_FALSE);
        this._tessDefaultStrOpts.put("load_system_dawg", TessBaseAPI.VAR_FALSE);
        this._tessDefaultStrOpts.put("language_model_penalty_non_dict_word", "0");
        this._tessDefaultStrOpts.put("language_model_penalty_non_freq_dict_word", "0");
        this._tessDefaultStrOpts.put("classify_enable_learning", TessBaseAPI.VAR_FALSE);
        this._tessDefaultStrOpts.put("classify_enable_adaptive_matcher", TessBaseAPI.VAR_FALSE);

        this._currScrPic = currScreenPicProv;

        this._cache = new LinkedHashMap<FuzzyComparablePicClip, String>() {
            protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 15;
            }
        };

        this._init(STARTUP_MODE);
    }

    private void _init(TesseractEngineMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode can't be null");
        }

        if (this._currentMode != mode) {
            this._tess.end();
            this._tess.init(this._trainDataPathParent, "eng", mode.intVal);
        }

        for (Map.Entry<String, String> opt : this._tessDefaultStrOpts.entrySet()) {
            this._setVar(opt.getKey(), opt.getValue());
        }
        this._currentMode = mode;
    }

    private void _setVar(String name, String value) {
        boolean r = this._tess.setVariable(name, value);
        if (!r) {
            this._log.error("can't set \"{}\" to \"{}\"", name, value);
        }
    }

    private void _copyDataFiles() {
        String fname = "eng.traineddata";
        File src = new File("tess", fname);
        File dst = new File(StaticConf.getInstance().tessTrainFilesDir, fname);
        IApkJarAssetLoader ldr = ApkJarAssetLoaderProv.getLoader();

        if (dst.exists()) {
            return;
        }

        InputStream srcIs = ldr.load(src.toString());
        BufferedOutputStream dstOs = Utils.fosFromFname(dst);
        Utils.copy(srcIs, dstOs);
    }

    private PicGrayscale _preprocessBitmap(PicData p, IAreaAbsPx a) {
        PicGrayscale grayscale;
        if (a != null) {
            AreaAbsPx aa = (AreaAbsPx) a;
            grayscale = PicGrayscale.create(aa.getWidth(), aa.getHeight());
            ColorConv.grayscale(p, grayscale, aa.left, aa.top);
        } else {
            grayscale = PicGrayscale.create(p.width, p.height);
            ColorConv.grayscale(p, grayscale);
        }
        return grayscale;
    }

    private String _recognizeThroughCache(RecognParams params, PicGrayscale pic) {
        FuzzyComparablePicClip keyPic = new FuzzyComparablePicClip(pic);
        String result = this._cache.get(keyPic);
        if (result == null) {
            result = this._recognize(params, pic);
            this._cache.put(keyPic, result);
        }

        return result;
    }

    public String recognize(RecognParams params, IAreaAbsPx a) throws InterruptedException {
        AreaAbsPx aa = (AreaAbsPx) a;
        Measure m = new Measure(String.format("ocr %d×%d %d,%d", aa.getWidth(), aa.getHeight(), aa.left, aa.top));
        PicData capture = this._currScrPic.getCurrScrPic().getLastCapture();
        PicGrayscale preprocBitmap = this._preprocessBitmap(capture, a);
        String r = this._recognizeThroughCache(params, preprocBitmap);
        m.done();
        m.setDetails(r.replace("\n", "\\n"));
        m.print(this._log, 0.01f, 1f);
        return r;
    }

    public String recognize(RecognParams params, IAutoPicData pic) {
        PicData p = ((AutoPicData) pic).getPic();
        Measure m = new Measure(String.format("ocr %d×%d", p.width, p.height));
        PicGrayscale preprocBitmap = this._preprocessBitmap(((AutoPicData)pic).getPic(), null);
        String r = this._recognizeThroughCache(params, preprocBitmap);
        m.done();
        m.setDetails(r.replace("\n", "\\n"));
        m.print(this._log, 0.01f, 1f);
        return r;
    }

    public String recognizeSnThrow(RecognParams params, IAreaAbsPx a) {
        String r = null;
        try {
            r = this.recognize(params, a);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Utils.throwChecked(e);
        }
        return r;
    }

    private String _recognize(RecognParams params, PicGrayscale grayCropped) {
        this._applyParams(params);
        this._tess.setImage(grayCropped.data, grayCropped.width, grayCropped.height, 1, grayCropped.width);
        return this._tess.getUTF8Text();
    }

    private void _applyParams(RecognParams params) {
        this._init(params.mode);
        this._setVar("tessedit_char_whitelist", params.charList);
    }
}
