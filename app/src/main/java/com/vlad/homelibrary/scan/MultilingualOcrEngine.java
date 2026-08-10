package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MultilingualOcrEngine {

    public static final class OcrResult {
        public final String fullText;
        public final List<String> lines;
        public final List<String> chooserOptions;

        public OcrResult(String fullText, List<String> lines, List<String> chooserOptions) {
            this.fullText = fullText;
            this.lines = lines;
            this.chooserOptions = chooserOptions;
        }

        public boolean isEmpty() {
            return fullText == null || fullText.isBlank();
        }
    }

    private final TessdataManager tessdataManager = new TessdataManager();

    public OcrResult recognize(Context context, Bitmap bitmap) throws Exception {
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
            // The scan frame is one block of text (e.g. a multi-line title).
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            tess.setImage(bitmap);
            String rawText = tess.getUTF8Text();
            return buildResult(rawText);
        } finally {
            tess.recycle();
        }
    }

    public TessdataManager getTessdataManager() {
        return tessdataManager;
    }

    static OcrResult buildResult(String rawText) {
        List<String> lines = splitAndCleanLines(rawText);
        String fullText = joinAsSingleField(lines);

        Set<String> options = new LinkedHashSet<>();
        if (!fullText.isEmpty()) {
            options.add(fullText);
        }
        // Keep single lines as secondary choices for short fields.
        if (lines.size() > 1) {
            options.addAll(lines);
        }

        return new OcrResult(fullText, lines, new ArrayList<>(options));
    }

    private static List<String> splitAndCleanLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] parts = text.split("\\R");
        for (String part : parts) {
            String cleaned = cleanFragment(part);
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private static String joinAsSingleField(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        return cleanFragment(String.join(" ", lines));
    }

    private static String cleanFragment(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        // Drop common OCR junk stuck to edges.
        cleaned = cleaned.replaceAll("^[|_=\\-•·]+", "");
        cleaned = cleaned.replaceAll("[|_=\\-•·]+$", "");
        // Collapse broken spacing inside a fragment.
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }
}
