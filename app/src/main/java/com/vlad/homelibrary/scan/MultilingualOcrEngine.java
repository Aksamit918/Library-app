package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

public class MultilingualOcrEngine {

    private static final String TAG = "HomeLibraryOCR";
    private static final int OCR_MIN_SIDE_PX = 480;
    private static final int OCR_MAX_SIDE_PX = 1920;
    private static final int LINE_MIN_SIDE_PX = 96;
    private static final String DIGIT_WHITELIST = "0123456789";

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

    public interface ProgressCallback {
        void onProgress(int current, int total, @NonNull String packLabel);
    }

    private static final class LocatedLine {
        final String text;
        final int top;
        final int left;
        final int height;

        LocatedLine(String text, int top) {
            this(text, top, 0, 0);
        }

        LocatedLine(String text, int top, int left, int height) {
            this.text = text;
            this.top = top;
            this.left = left;
            this.height = height;
        }
    }

    private final TessdataManager tessdataManager = new TessdataManager();

    public OcrResult recognize(Context context, Bitmap bitmap) throws Exception {
        return recognize(context, bitmap, OcrScriptChoice.defaultForDeviceLocale(), null);
    }

    public OcrResult recognize(Context context,
                               Bitmap bitmap,
                               @NonNull OcrScriptChoice script,
                               @Nullable ProgressCallback progress)
            throws Exception {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("Bitmap is null");
        }

        Log.i(TAG, "OCR start script=" + script.id
                + " " + bitmap.getWidth() + "x" + bitmap.getHeight());

        Bitmap prepared = OcrImagePrep.fitForOcr(bitmap, OCR_MIN_SIDE_PX, OCR_MAX_SIDE_PX);
        boolean recyclePrepared = prepared != bitmap;
        List<OcrImagePrep.TextBand> bands = Collections.emptyList();
        try {
            bands = OcrImagePrep.detectTextBands(prepared);
            Log.i(TAG, "Detected " + bands.size() + " text bands");

            OcrResult result = emptyResult();
            if (script.mlKitScript == OcrScriptChoice.MlKitScript.LATIN) {
                result = recognizeWithMlKitCover(prepared, bands, latinRecognizer(), true);
            } else if (script.mlKitScript == OcrScriptChoice.MlKitScript.CJK) {
                result = recognizeWithMlKitCjk(prepared, bands);
            }

            if (script.usesTess()) {
                OcrResult tess = recognizeWithTess(context, prepared, bands, script, progress);
                result = mergeResults(result, tess);
            }

            if (!result.isEmpty()) {
                Log.i(TAG, "OCR winner [" + script.id + "]: " + safePreview(result.fullText)
                        + " lines=" + result.lines.size());
                return result;
            }

            Log.w(TAG, "OCR produced no usable text for " + script.id);
            return emptyResult();
        } finally {
            for (OcrImagePrep.TextBand band : bands) {
                OcrImagePrep.recycleQuietly(band.bitmap, prepared);
            }
            if (recyclePrepared && !prepared.isRecycled()) {
                prepared.recycle();
            }
        }
    }

    @NonNull
    private static TextRecognizer latinRecognizer() {
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    @NonNull
    private static OcrResult recognizeWithMlKitCjk(@NonNull Bitmap bitmap,
                                                   @NonNull List<OcrImagePrep.TextBand> bands)
            throws Exception {
        TextRecognizer[] recognizers = new TextRecognizer[]{
                TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build())
        };
        OcrResult best = emptyResult();
        int bestScore = Integer.MIN_VALUE;
        try {
            for (int i = 0; i < recognizers.length; i++) {
                OcrResult candidate = recognizeWithMlKitCover(
                        bitmap, bands, recognizers[i], false);
                int score = countScript(candidate.fullText, cp -> isCjk(cp) || isHangul(cp)) * 10
                        + countAlnum(candidate.fullText);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        } finally {
            for (TextRecognizer recognizer : recognizers) {
                recognizer.close();
            }
        }
        return best;
    }

    @NonNull
    private static OcrResult recognizeWithMlKitCover(@NonNull Bitmap bitmap,
                                                     @NonNull List<OcrImagePrep.TextBand> bands,
                                                     @NonNull TextRecognizer recognizer,
                                                     boolean closeRecognizer) throws Exception {
        List<LocatedLine> located = new ArrayList<>();
        try {
            Bitmap polarity = OcrImagePrep.ensureDarkTextOnLight(bitmap);
            try {
                collectMlKit(recognizer, polarity, 0, located);
                if (polarity == bitmap) {
                    Bitmap minChannel = OcrImagePrep.minChannelGray(bitmap);
                    try {
                        collectMlKit(recognizer, minChannel, 0, located);
                    } finally {
                        OcrImagePrep.recycleQuietly(minChannel, bitmap);
                    }
                }
            } finally {
                OcrImagePrep.recycleQuietly(polarity, bitmap);
            }
            for (OcrImagePrep.TextBand band : bands) {
                Bitmap scaled = OcrImagePrep.fitForOcr(band.bitmap, LINE_MIN_SIDE_PX, OCR_MAX_SIDE_PX);
                try {
                    Bitmap bandPolarity = OcrImagePrep.ensureDarkTextOnLight(scaled);
                    try {
                        collectMlKit(recognizer, bandPolarity, band.top, located);
                    } finally {
                        OcrImagePrep.recycleQuietly(bandPolarity, scaled);
                    }
                } finally {
                    OcrImagePrep.recycleQuietly(scaled, band.bitmap);
                }
            }
        } finally {
            if (closeRecognizer) {
                recognizer.close();
            }
        }
        return buildResultFromLocated(located);
    }

    private static void collectMlKit(@NonNull TextRecognizer recognizer,
                                     @NonNull Bitmap bitmap,
                                     int topOffset,
                                     @NonNull List<LocatedLine> out) throws Exception {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Text text = Tasks.await(recognizer.process(image), 20, TimeUnit.SECONDS);
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String cleaned = OcrTextNormalizer.normalizeAuthorInitials(line.getText());
                if (!OcrTextNormalizer.isKeepableLine(cleaned)) {
                    continue;
                }
                int top = topOffset;
                int left = 0;
                int height = 0;
                if (line.getBoundingBox() != null) {
                    Rect box = line.getBoundingBox();
                    top += box.top;
                    left = box.left;
                    height = box.height();
                }
                out.add(new LocatedLine(cleaned, top, left, height));
            }
        }
    }

    @NonNull
    private OcrResult recognizeWithTess(Context context,
                                        Bitmap image,
                                        List<OcrImagePrep.TextBand> bands,
                                        OcrScriptChoice script,
                                        @Nullable ProgressCallback progress) throws Exception {
        if (script.tessLanguageCodes == null || script.tessInitLangs == null) {
            return emptyResult();
        }
        tessdataManager.ensureLanguageData(
                context,
                script.tessLanguageCodes,
                (current, total, languageCode) -> {
                    if (progress != null) {
                        progress.onProgress(current, total, languageCode);
                    }
                });

        String dataPath = tessdataManager.getTessParentDir(context).getAbsolutePath();
        TessBaseAPI tess = new TessBaseAPI();
        List<LocatedLine> located = new ArrayList<>();
        try {
            if (!tess.init(dataPath, script.tessInitLangs, TessBaseAPI.OEM_LSTM_ONLY)) {
                Log.w(TAG, "Tess init failed for " + script.tessInitLangs);
                return emptyResult();
            }
            tess.setVariable("preserve_interword_spaces", "1");
            tess.setVariable("user_defined_dpi", "300");
            tess.setVariable("tessedit_do_invert", "0");
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "");
            tess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "");

            collectTessVariants(tess, image, 0, false, located);
            collectDigitPass(tess, image, 0, located);
            for (OcrImagePrep.TextBand band : bands) {
                Bitmap scaled = OcrImagePrep.fitForOcr(band.bitmap, LINE_MIN_SIDE_PX, OCR_MAX_SIDE_PX);
                try {
                    collectTessVariants(tess, scaled, band.top, true, located);
                    collectDigitPass(tess, scaled, band.top, located);
                } finally {
                    OcrImagePrep.recycleQuietly(scaled, band.bitmap);
                }
            }
        } finally {
            tess.recycle();
        }
        return buildResultFromLocated(located);
    }

    private static void collectTessVariants(TessBaseAPI tess,
                                            Bitmap source,
                                            int topOffset,
                                            boolean singleLine,
                                            List<LocatedLine> out) {
        List<Bitmap> variants = new ArrayList<>();
        boolean darkCover = OcrImagePrep.isMostlyDark(source);
        Bitmap inverted = OcrImagePrep.ensureDarkTextOnLight(source);
        if (darkCover) {
            variants.add(inverted);
        } else {
            variants.add(source);
            variants.add(OcrImagePrep.minChannelGray(source));
            if (singleLine) {
                variants.add(OcrImagePrep.chromaAsDarkGray(source));
            }
        }
        try {
            int[] pageSegModes = pageSegModesFor(source, singleLine);
            int before = out.size();
            for (Bitmap variant : variants) {
                for (int psm : pageSegModes) {
                    collectTess(tess, variant, psm, topOffset, out);
                    if (singleLine && addedStableLetterLine(out, before)) {
                        break;
                    }
                }
            }
        } finally {
            for (Bitmap variant : variants) {
                OcrImagePrep.recycleQuietly(variant, source);
            }
        }
    }

    @NonNull
    private static int[] pageSegModesFor(@NonNull Bitmap source, boolean singleLine) {
        boolean wideLine = source.getWidth() > source.getHeight() * 2;
        boolean compactNumber = source.getWidth() < source.getHeight() * 4
                && source.getHeight() >= 24;
        if (singleLine) {
            if (compactNumber && !wideLine) {
                return new int[]{
                        TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                        TessBaseAPI.PageSegMode.PSM_SINGLE_WORD,
                        TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                };
            }
            return new int[]{
                    TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                    TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            };
        }
        if (wideLine) {
            return new int[]{
                    TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                    TessBaseAPI.PageSegMode.PSM_AUTO,
                    TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            };
        }
        return new int[]{
                TessBaseAPI.PageSegMode.PSM_AUTO,
                TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
        };
    }

    private static boolean addedStableLetterLine(List<LocatedLine> lines, int fromIndex) {
        for (int i = fromIndex; i < lines.size(); i++) {
            String text = lines.get(i).text;
            if (OcrTextNormalizer.isMostlyDigits(text)
                    || OcrTextNormalizer.looksLikeInitials(text)) {
                continue;
            }
            if (countScript(text, Character::isLetter) >= 6) {
                return true;
            }
        }
        return false;
    }

    private static void collectDigitPass(TessBaseAPI tess,
                                         Bitmap source,
                                         int topOffset,
                                         List<LocatedLine> out) {
        Bitmap polarity = OcrImagePrep.ensureDarkTextOnLight(source);
        tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, DIGIT_WHITELIST);
        try {
            int[] modes = {
                    TessBaseAPI.PageSegMode.PSM_SINGLE_WORD,
                    TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                    TessBaseAPI.PageSegMode.PSM_RAW_LINE
            };
            for (int psm : modes) {
                tess.setPageSegMode(psm);
                tess.setImage(polarity);
                String raw = tess.getUTF8Text();
                String digits = OcrTextNormalizer.extractDigitRun(raw);
                if (digits.length() >= 2) {
                    out.add(new LocatedLine(
                            digits,
                            topOffset,
                            0,
                            polarity.getHeight()
                    ));
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Digit pass failed", e);
        } finally {
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "");
            OcrImagePrep.recycleQuietly(polarity, source);
        }
    }

    private static void collectTess(TessBaseAPI tess,
                                    Bitmap prepared,
                                    int pageSegMode,
                                    int topOffset,
                                    List<LocatedLine> out) {
        try {
            tess.setPageSegMode(pageSegMode);
            tess.setImage(prepared);
            tess.getUTF8Text();
            ResultIterator iterator = tess.getResultIterator();
            if (iterator == null) {
                collectTessFromPlainText(tess, topOffset, out);
                return;
            }
            try {
                int level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE;
                iterator.begin();
                do {
                    String part = iterator.getUTF8Text(level);
                    if (part == null) {
                        continue;
                    }
                    String cleaned = OcrTextNormalizer.normalizeAuthorInitials(part);
                    if (!OcrTextNormalizer.isKeepableLine(cleaned)) {
                        continue;
                    }
                    Rect box = iterator.getBoundingRect(level);
                    int top = topOffset;
                    int left = 0;
                    int height = 0;
                    if (box != null) {
                        top += box.top;
                        left = box.left;
                        height = box.height();
                    }
                    out.add(new LocatedLine(cleaned, top, left, height));
                } while (iterator.next(level));
            } finally {
                iterator.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Tess page failed psm=" + pageSegMode, e);
        }
    }

    private static void collectTessFromPlainText(TessBaseAPI tess,
                                                 int topOffset,
                                                 List<LocatedLine> out) {
        String raw = tess.getUTF8Text();
        int lineTop = topOffset;
        for (String part : splitAndCleanLines(raw)) {
            String cleaned = OcrTextNormalizer.normalizeAuthorInitials(part);
            if (!OcrTextNormalizer.isKeepableLine(cleaned)) {
                continue;
            }
            out.add(new LocatedLine(cleaned, lineTop));
            lineTop += 8;
        }
    }

    @NonNull
    private static OcrResult mergeResults(@NonNull OcrResult first, @NonNull OcrResult second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        List<LocatedLine> located = new ArrayList<>();
        int top = 0;
        for (String line : first.lines) {
            located.add(new LocatedLine(line, top));
            top += 10;
        }
        for (String line : second.lines) {
            located.add(new LocatedLine(line, top));
            top += 10;
        }
        return buildResultFromLocated(located);
    }

    @NonNull
    private static OcrResult buildResultFromLocated(List<LocatedLine> located) {
        List<String> merged = mergeSimilar(located);
        merged = joinSameVisualLine(located, merged);
        List<String> normalized = new ArrayList<>();
        for (String line : merged) {
            normalized.add(OcrTextNormalizer.normalizeAuthorInitials(line));
        }
        return buildResultFromLines(mergeSimilarTexts(normalized));
    }

    @NonNull
    private static List<String> mergeSimilar(List<LocatedLine> located) {
        List<LocatedLine> sorted = new ArrayList<>(located);
        sorted.sort(Comparator
                .comparingInt((LocatedLine line) -> line.top)
                .thenComparingInt(line -> line.left)
                .thenComparingInt(line -> -line.text.length()));
        return mergeSimilarTexts(toTexts(sorted));
    }

    @NonNull
    private static List<String> mergeSimilarTexts(List<String> lines) {
        List<String> merged = new ArrayList<>();
        for (String candidate : lines) {
            String cleaned = OcrTextNormalizer.cleanFragment(candidate);
            int index = OcrTextNormalizer.indexOfSimilar(merged, cleaned);
            if (index < 0) {
                merged.add(cleaned);
                continue;
            }
            if (shouldReplace(merged.get(index), cleaned)) {
                merged.set(index, cleaned);
            }
        }
        return merged;
    }

    private static boolean shouldReplace(String current, String candidate) {
        if (OcrTextNormalizer.isMostlyDigits(candidate)
                && OcrTextNormalizer.isMostlyDigits(current)) {
            return candidate.length() >= current.length();
        }
        return OcrTextNormalizer.countAlnum(candidate) > OcrTextNormalizer.countAlnum(current);
    }

    @NonNull
    private static List<String> joinSameVisualLine(List<LocatedLine> located, List<String> merged) {
        List<LocatedLine> withBoxes = new ArrayList<>();
        for (LocatedLine line : located) {
            if (line.height <= 0) {
                continue;
            }
            String cleaned = OcrTextNormalizer.cleanFragment(line.text);
            if (!OcrTextNormalizer.isKeepableLine(cleaned)
                    && !OcrTextNormalizer.looksLikeInitials(cleaned)) {
                continue;
            }
            withBoxes.add(new LocatedLine(cleaned, line.top, line.left, line.height));
        }
        if (withBoxes.size() < 2) {
            return merged;
        }
        withBoxes.sort(Comparator
                .comparingInt((LocatedLine line) -> line.top)
                .thenComparingInt(line -> line.left));

        List<LocatedLine> joined = new ArrayList<>();
        for (LocatedLine line : withBoxes) {
            if (joined.isEmpty()) {
                joined.add(line);
                continue;
            }
            LocatedLine prev = joined.get(joined.size() - 1);
            if (sameVisualLine(prev, line)
                    && !OcrTextNormalizer.isMostlyDigits(prev.text)
                    && !OcrTextNormalizer.isMostlyDigits(line.text)
                    && !containsLetters(prev.text, line.text)
                    && !containsLetters(line.text, prev.text)) {
                String leftText = prev.left <= line.left ? prev.text : line.text;
                String rightText = prev.left <= line.left ? line.text : prev.text;
                joined.set(joined.size() - 1, new LocatedLine(
                        OcrTextNormalizer.cleanFragment(leftText + " " + rightText),
                        Math.min(prev.top, line.top),
                        Math.min(prev.left, line.left),
                        Math.max(prev.height, line.height)
                ));
            } else {
                joined.add(line);
            }
        }
        List<String> combined = new ArrayList<>(merged);
        for (LocatedLine line : joined) {
            if (OcrTextNormalizer.indexOfSimilar(combined, line.text) < 0) {
                combined.add(line.text);
            }
        }
        return combined;
    }

    private static boolean sameVisualLine(@NonNull LocatedLine a, @NonNull LocatedLine b) {
        if (a.height <= 0 || b.height <= 0) {
            return false;
        }
        int minH = Math.min(a.height, b.height);
        int maxH = Math.max(a.height, b.height);
        if (minH * 10 < maxH * 6) {
            return false;
        }
        return Math.abs(a.top - b.top) <= Math.max(6, minH / 2);
    }

    private static boolean containsLetters(String a, String b) {
        String keyA = OcrTextNormalizer.lettersKey(a);
        String keyB = OcrTextNormalizer.lettersKey(b);
        return !keyA.isEmpty() && !keyB.isEmpty() && keyA.contains(keyB);
    }

    @NonNull
    private static List<String> toTexts(List<LocatedLine> located) {
        List<String> texts = new ArrayList<>(located.size());
        for (LocatedLine line : located) {
            texts.add(line.text);
        }
        return texts;
    }

    private static int countAlnum(@Nullable String text) {
        return OcrTextNormalizer.countAlnum(text);
    }

    private static int countScript(@Nullable String text, IntPredicate matcher) {
        return OcrTextNormalizer.countScript(text, matcher);
    }

    private static boolean isHangul(int cp) {
        return cp >= 0xAC00 && cp <= 0xD7AF;
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0x3400 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF);
    }

    @NonNull
    private static OcrResult buildResultFromLines(List<String> lines) {
        List<String> filtered = filterLines(lines);
        String fullText = filtered.isEmpty() ? "" : String.join(" ", filtered);
        Set<String> options = new LinkedHashSet<>();
        if (!fullText.isEmpty()) {
            options.add(fullText);
        }
        if (filtered.size() > 1) {
            options.addAll(filtered);
        }
        return new OcrResult(fullText, filtered, new ArrayList<>(options));
    }

    private static List<String> filterLines(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            for (String part : OcrTextNormalizer.explodeMixedNumberLine(line)) {
                String cleaned = OcrTextNormalizer.normalizeAuthorInitials(part);
                if (OcrTextNormalizer.isKeepableLine(cleaned)
                        && OcrTextNormalizer.indexOfSimilar(out, cleaned) < 0) {
                    out.add(cleaned);
                }
            }
        }
        return dropCoveredFragments(out);
    }

    @NonNull
    private static List<String> dropCoveredFragments(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (OcrTextNormalizer.isMostlyDigits(line)) {
                out.add(line);
                continue;
            }
            String key = OcrTextNormalizer.lettersKey(line);
            boolean covered = false;
            if (OcrTextNormalizer.looksLikeInitials(line) || key.length() <= 4) {
                for (int j = 0; j < lines.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    String other = OcrTextNormalizer.lettersKey(lines.get(j));
                    if (other.length() > key.length() && other.contains(key)) {
                        covered = true;
                        break;
                    }
                }
            }
            if (!covered) {
                out.add(line);
            }
        }
        return out;
    }

    private static List<String> splitAndCleanLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String part : text.split("\\R")) {
            String cleaned = OcrTextNormalizer.cleanFragment(part);
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private static String safePreview(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    @NonNull
    private static OcrResult emptyResult() {
        return new OcrResult("", new ArrayList<>(), new ArrayList<>());
    }
}
