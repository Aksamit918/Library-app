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

public class MultilingualOcrEngine {

    private static final String TAG = "HomeLibraryOCR";
    private static final int OCR_MIN_SIDE_PX = 560;
    private static final int OCR_MAX_SIDE_PX = 1600;
    private static final int LINE_MIN_SIDE_PX = 88;
    private static final float MIN_LINE_CONFIDENCE = 32f;

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
        final float confidence;

        LocatedLine(String text, int top, int left, int height, float confidence) {
            this.text = text;
            this.top = top;
            this.left = left;
            this.height = height;
            this.confidence = confidence;
        }
    }

    private final TessdataManager tessdataManager = new TessdataManager();

    public OcrResult recognize(Context context, Bitmap bitmap) throws Exception {
        return recognize(context, bitmap, OcrScriptChoice.defaultForAppLocale(context), null);
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
        Bitmap gray = OcrImagePrep.toGray(prepared);
        boolean recycleGray = gray != prepared && gray != bitmap;
        Bitmap inverted = OcrImagePrep.invert(gray);
        List<OcrImagePrep.TextBand> bands = Collections.emptyList();
        try {
            List<LocatedLine> located = new ArrayList<>();
            TextRecognizer mlKit = recognizerFor(script.mlKitScript);
            if (mlKit != null) {
                collectMlKitCover(gray, inverted, mlKit, located);
            }

            if (script.usesTess()) {
                try {
                    collectTessCover(context, gray, inverted, script, progress, located);
                } catch (Exception e) {
                    Log.w(TAG, "Tess failed for " + script.id, e);
                    if (located.isEmpty()) {
                        throw e;
                    }
                }
            }

            if (!hasRealContent(located, script.family)) {
                Bitmap bandSource = OcrImagePrep.isMostlyDark(prepared) ? inverted : gray;
                bands = OcrImagePrep.detectTextBands(bandSource);
                Log.i(TAG, "Fallback bands=" + bands.size());
                if (script.usesTess() && !bands.isEmpty()) {
                    collectTessBands(context, bandSource, bands, script, located);
                }
            }

            OcrResult result = buildResultFromLocated(located, script.family);
            if (!result.isEmpty()) {
                Log.i(TAG, "OCR winner [" + script.id + "]: " + safePreview(result.fullText)
                        + " lines=" + result.lines.size());
                return result;
            }

            Log.w(TAG, "OCR produced no usable text for " + script.id);
            return emptyResult();
        } finally {
            for (OcrImagePrep.TextBand band : bands) {
                OcrImagePrep.recycleQuietly(band.bitmap, inverted);
            }
            OcrImagePrep.recycleQuietly(inverted, gray);
            if (recycleGray && !gray.isRecycled()) {
                gray.recycle();
            }
            if (recyclePrepared && !prepared.isRecycled()) {
                prepared.recycle();
            }
        }
    }

    @Nullable
    private static TextRecognizer recognizerFor(@NonNull OcrScriptChoice.MlKitScript script) {
        switch (script) {
            case LATIN:
                return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            case CHINESE:
                return TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            case JAPANESE:
                return TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            case KOREAN:
                return TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
            default:
                return null;
        }
    }

    private static void collectMlKitCover(@NonNull Bitmap gray,
                                          @NonNull Bitmap inverted,
                                          @NonNull TextRecognizer recognizer,
                                          @NonNull List<LocatedLine> located) throws Exception {
        try {
            List<LocatedLine> grayLines = new ArrayList<>();
            List<LocatedLine> invLines = new ArrayList<>();
            collectMlKit(recognizer, gray, 0, grayLines);
            collectMlKit(recognizer, inverted, 0, invLines);
            located.addAll(scoreLines(grayLines) >= scoreLines(invLines) ? grayLines : invLines);
        } finally {
            recognizer.close();
        }
    }

    private static void collectMlKit(@NonNull TextRecognizer recognizer,
                                     @NonNull Bitmap bitmap,
                                     int topOffset,
                                     @NonNull List<LocatedLine> out) throws Exception {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Text text = Tasks.await(recognizer.process(image), 20, TimeUnit.SECONDS);
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                addLocated(out, line.getText(), topOffset, line.getBoundingBox(), 70f);
            }
        }
    }

    private void collectTessCover(Context context,
                                  Bitmap gray,
                                  Bitmap inverted,
                                  OcrScriptChoice script,
                                  @Nullable ProgressCallback progress,
                                  List<LocatedLine> located) throws Exception {
        if (script.tessLanguageCodes == null || script.tessInitLangs == null) {
            return;
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
        try {
            if (!tess.init(dataPath, script.tessInitLangs, TessBaseAPI.OEM_LSTM_ONLY)) {
                Log.w(TAG, "Tess init failed for " + script.tessInitLangs);
                return;
            }
            tess.setVariable("preserve_interword_spaces", "1");
            tess.setVariable("user_defined_dpi", "300");
            tess.setVariable("tessedit_do_invert", "0");
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "");
            tess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "");

            List<LocatedLine> best = Collections.emptyList();
            int bestScore = Integer.MIN_VALUE;
            Bitmap[] variants = {gray, inverted};
            int[] modes = {
                    TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                    TessBaseAPI.PageSegMode.PSM_AUTO
            };
            for (Bitmap variant : variants) {
                for (int psm : modes) {
                    List<LocatedLine> lines = new ArrayList<>();
                    int mean = collectTess(tess, variant, psm, 0, lines);
                    int score = scoreAttempt(lines, mean, script.family);
                    Log.i(TAG, "Tess pass psm=" + psm
                            + " mean=" + mean
                            + " score=" + score
                            + " lines=" + lines.size());
                    if (score > bestScore) {
                        bestScore = score;
                        best = lines;
                    }
                }
            }
            located.addAll(best);
        } finally {
            tess.recycle();
        }
    }

    private void collectTessBands(Context context,
                                  Bitmap bandSource,
                                  List<OcrImagePrep.TextBand> bands,
                                  OcrScriptChoice script,
                                  List<LocatedLine> located) {
        if (script.tessLanguageCodes == null || script.tessInitLangs == null) {
            return;
        }
        String dataPath = tessdataManager.getTessParentDir(context).getAbsolutePath();
        TessBaseAPI tess = new TessBaseAPI();
        try {
            if (!tess.init(dataPath, script.tessInitLangs, TessBaseAPI.OEM_LSTM_ONLY)) {
                return;
            }
            tess.setVariable("preserve_interword_spaces", "1");
            tess.setVariable("user_defined_dpi", "300");
            tess.setVariable("tessedit_do_invert", "0");
            for (OcrImagePrep.TextBand band : bands) {
                Bitmap scaled = OcrImagePrep.fitForOcr(band.bitmap, LINE_MIN_SIDE_PX, OCR_MAX_SIDE_PX);
                try {
                    collectTess(tess, scaled, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, band.top, located);
                } finally {
                    OcrImagePrep.recycleQuietly(scaled, band.bitmap);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Band tess failed", e);
        } finally {
            tess.recycle();
        }
    }

    private static int scoreAttempt(@NonNull List<LocatedLine> lines,
                                    int meanConfidence,
                                    @NonNull OcrScriptChoice.Family family) {
        int score = meanConfidence;
        int useful = 0;
        for (LocatedLine line : lines) {
            if (OcrTextNormalizer.mismatchesChosenScript(line.text, family)
                    || OcrTextNormalizer.looksLikeGibberish(line.text)
                    || OcrTextNormalizer.isMixedScriptNoise(line.text)) {
                score -= 20;
                continue;
            }
            if (OcrTextNormalizer.isKeepableLine(line.text)) {
                useful++;
                score += 30 + OcrTextNormalizer.countScript(line.text, Character::isLetter)
                        + Math.round(line.confidence / 2f);
            }
        }
        if (useful == 0) {
            score -= 50;
        }
        return score;
    }

    private static int scoreLines(@NonNull List<LocatedLine> lines) {
        int score = 0;
        for (LocatedLine line : lines) {
            score += OcrTextNormalizer.lineScore(line.text, line.confidence);
        }
        return score;
    }

    private static boolean hasRealContent(@NonNull List<LocatedLine> located,
                                          @NonNull OcrScriptChoice.Family family) {
        for (LocatedLine line : located) {
            if (OcrTextNormalizer.isKeepableLine(line.text)
                    && !OcrTextNormalizer.mismatchesChosenScript(line.text, family)
                    && !OcrTextNormalizer.looksLikeGibberish(line.text)) {
                return true;
            }
        }
        return false;
    }

    private static int collectTess(TessBaseAPI tess,
                                   Bitmap prepared,
                                   int pageSegMode,
                                   int topOffset,
                                   List<LocatedLine> out) {
        int mean = 0;
        try {
            tess.setPageSegMode(pageSegMode);
            tess.setImage(prepared);
            tess.getUTF8Text();
            try {
                mean = tess.meanConfidence();
            } catch (Exception ignored) {
            }
            ResultIterator iterator = tess.getResultIterator();
            if (iterator == null) {
                for (String part : splitAndCleanLines(tess.getUTF8Text())) {
                    addLocated(out, part, topOffset, null, mean);
                }
                return mean;
            }
            try {
                int level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE;
                iterator.begin();
                do {
                    String part = iterator.getUTF8Text(level);
                    if (part == null) {
                        continue;
                    }
                    float confidence = mean;
                    try {
                        confidence = iterator.confidence(level);
                    } catch (Exception ignored) {
                    }
                    addLocated(out, part, topOffset, iterator.getBoundingRect(level), confidence);
                } while (iterator.next(level));
            } finally {
                iterator.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Tess page failed psm=" + pageSegMode, e);
        }
        return mean;
    }

    private static void addLocated(@NonNull List<LocatedLine> out,
                                   @Nullable String raw,
                                   int topOffset,
                                   @Nullable Rect box,
                                   float confidence) {
        String cleaned = OcrTextNormalizer.cleanOcrLine(raw);
        if (!OcrTextNormalizer.isKeepableLine(cleaned)) {
            return;
        }
        int letters = OcrTextNormalizer.countScript(cleaned, Character::isLetter);
        int digits = OcrTextNormalizer.countScript(cleaned, Character::isDigit);
        if (confidence < MIN_LINE_CONFIDENCE && letters < 6 && digits < 4) {
            return;
        }
        int top = topOffset;
        int left = 0;
        int height = 0;
        if (box != null) {
            top += box.top;
            left = box.left;
            height = box.height();
        }
        out.add(new LocatedLine(cleaned, top, left, height, confidence));
    }

    @NonNull
    private static OcrResult buildResultFromLocated(List<LocatedLine> located,
                                                    @NonNull OcrScriptChoice.Family family) {
        List<LocatedLine> clustered = clusterBestLines(located);
        List<String> merged = new ArrayList<>();
        for (LocatedLine line : clustered) {
            if (OcrTextNormalizer.mismatchesChosenScript(line.text, family)
                    || OcrTextNormalizer.looksLikeGibberish(line.text)
                    || OcrTextNormalizer.isMixedScriptNoise(line.text)) {
                continue;
            }
            String cleaned = OcrTextNormalizer.cleanOcrLine(line.text);
            if (!OcrTextNormalizer.isKeepableLine(cleaned)) {
                continue;
            }
            int index = OcrTextNormalizer.indexOfSimilar(merged, cleaned);
            if (index < 0) {
                merged.add(cleaned);
                continue;
            }
            if (shouldReplace(merged.get(index), cleaned)) {
                merged.set(index, cleaned);
            }
        }
        return buildResultFromLines(merged);
    }

    @NonNull
    private static List<LocatedLine> clusterBestLines(List<LocatedLine> located) {
        List<LocatedLine> sorted = new ArrayList<>();
        for (LocatedLine line : located) {
            if (OcrTextNormalizer.isKeepableLine(line.text)) {
                sorted.add(line);
            }
        }
        sorted.sort(Comparator
                .comparingInt((LocatedLine line) -> line.top)
                .thenComparingInt(line -> line.left)
                .thenComparingInt(line -> -OcrTextNormalizer.lineScore(line.text, line.confidence)));

        List<LocatedLine> clustered = new ArrayList<>();
        for (LocatedLine line : sorted) {
            int index = findSameRow(clustered, line);
            if (index < 0) {
                clustered.add(line);
                continue;
            }
            LocatedLine prev = clustered.get(index);
            if (canJoin(prev, line)) {
                String leftText = prev.left <= line.left ? prev.text : line.text;
                String rightText = prev.left <= line.left ? line.text : prev.text;
                clustered.set(index, new LocatedLine(
                        OcrTextNormalizer.cleanOcrLine(leftText + " " + rightText),
                        Math.min(prev.top, line.top),
                        Math.min(prev.left, line.left),
                        Math.max(prev.height, line.height),
                        Math.max(prev.confidence, line.confidence)
                ));
            } else if (OcrTextNormalizer.lineScore(line.text, line.confidence)
                    > OcrTextNormalizer.lineScore(prev.text, prev.confidence)) {
                clustered.set(index, line);
            }
        }
        return clustered;
    }

    private static int findSameRow(@NonNull List<LocatedLine> clustered, @NonNull LocatedLine line) {
        for (int i = 0; i < clustered.size(); i++) {
            if (sameVisualLine(clustered.get(i), line)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean canJoin(@NonNull LocatedLine a, @NonNull LocatedLine b) {
        if (OcrTextNormalizer.isMostlyDigits(a.text) || OcrTextNormalizer.isMostlyDigits(b.text)) {
            return false;
        }
        String keyA = OcrTextNormalizer.lettersKey(a.text);
        String keyB = OcrTextNormalizer.lettersKey(b.text);
        if (keyA.isEmpty() || keyB.isEmpty()) {
            return false;
        }
        if (keyA.contains(keyB) || keyB.contains(keyA) || OcrTextNormalizer.isNoisyVariant(keyA, keyB)) {
            return false;
        }
        return Math.abs(a.left - b.left) > Math.max(12, Math.min(a.height, b.height));
    }

    private static boolean shouldReplace(String current, String candidate) {
        if (OcrTextNormalizer.isMostlyDigits(candidate)
                && OcrTextNormalizer.isMostlyDigits(current)) {
            return candidate.length() >= current.length();
        }
        return OcrTextNormalizer.countAlnum(candidate) > OcrTextNormalizer.countAlnum(current);
    }

    private static boolean sameVisualLine(@NonNull LocatedLine a, @NonNull LocatedLine b) {
        int minH = Math.min(Math.max(a.height, 12), Math.max(b.height, 12));
        return Math.abs(a.top - b.top) <= Math.max(8, minH / 2);
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
                String cleaned = OcrTextNormalizer.cleanOcrLine(part);
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
            for (int j = 0; j < lines.size(); j++) {
                if (i == j) {
                    continue;
                }
                String other = OcrTextNormalizer.lettersKey(lines.get(j));
                if (other.length() > key.length()
                        && other.contains(key)
                        && (OcrTextNormalizer.looksLikeInitials(line) || key.length() <= 4
                        || OcrTextNormalizer.isNoisyVariant(key, other))) {
                    covered = true;
                    break;
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
