package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.util.ArrayList;
import java.util.List;

public class MultilingualOcrEngine {

    private final TessdataManager tessdataManager = new TessdataManager();

    public List<String> recognizeLines(Context context, Bitmap bitmap) throws Exception {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap is null");
        }

        if (!tessdataManager.hasAllLanguageData(context)) {
            tessdataManager.ensureLanguageData(context, null);
        }

        TessBaseAPI tess = new TessBaseAPI();
        try {
            String dataPath = tessdataManager.getTessParentDir(context).getAbsolutePath();
            if (!tess.init(dataPath, TessdataManager.TESSERACT_LANGS)) {
                throw new IllegalStateException("Failed to init Tesseract");
            }
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            tess.setImage(bitmap);
            String text = tess.getUTF8Text();
            return splitLines(text);
        } finally {
            tess.recycle();
        }
    }

    public TessdataManager getTessdataManager() {
        return tessdataManager;
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] parts = text.split("\\R");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }
}
