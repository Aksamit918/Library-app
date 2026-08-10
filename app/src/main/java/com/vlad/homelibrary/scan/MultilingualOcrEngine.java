package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MultilingualOcrEngine {

    private static final String TAG = "HomeLibraryOCR";
    private static final int MIN_OCR_SIDE_PX = 960;

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

    private final PaddleOcrModelManager paddleModels = new PaddleOcrModelManager();
    private final TessdataManager tessdataManager = new TessdataManager();

    public PaddleOcrModelManager getPaddleModelManager() {
        return paddleModels;
    }

    public OcrResult recognize(Context context, Bitmap bitmap) throws Exception {
        return recognize(context, bitmap, OcrScriptChoice.defaultForDeviceLocale(), null);
    }

    public OcrResult recognize(Context context,
                               Bitmap bitmap,
                               @NonNull OcrScriptChoice script,
                               @Nullable PaddleOcrModelManager.ProgressCallback progress)
            throws Exception {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("Bitmap is null");
        }

        Log.i(TAG, "OCR start script=" + script.id
                + " " + bitmap.getWidth() + "x" + bitmap.getHeight());

        Bitmap upscaled = OcrImagePrep.upscaleMinSide(bitmap, MIN_OCR_SIDE_PX);
        boolean recycleUpscaled = upscaled != bitmap;

        Map<PaddleOcrModelManager.ScriptPack, PaddleRecEngine> engines = new HashMap<>();
        List<Bitmap> boxCrops = new ArrayList<>();
        try {
            ensureModels(context, script, progress);

            if (script.usesPaddle()) {
                boxCrops = detectLineCrops(context, upscaled);
            }

            ScoredOcr best = ScoredOcr.empty();
            List<Bitmap> wholeOnly = Collections.singletonList(upscaled);

            if (script.usesPaddle() && script.paddlePacks != null) {
                ScoredOcr paddleWhole = recognizeCrops(
                        context, wholeOnly, upscaled, script.paddlePacks, engines);
                best = preferBetter(best, paddleWhole);

                if (!boxCrops.isEmpty() && boxCrops.size() <= 12) {
                    ScoredOcr paddleBoxes = recognizeCrops(
                            context, boxCrops, upscaled, script.paddlePacks, engines);
                    if (qualityScore(paddleBoxes) > qualityScore(best) + 15) {
                        best = paddleBoxes;
                    }
                }
            }

            if (script.usesTess()) {
                ScoredOcr tess = tryTessForScript(context, upscaled, script, progress);
                best = preferBetter(best, tess);
            }

            best = sanitizeResult(best);
            if (!best.result.isEmpty()) {
                Log.i(TAG, "OCR winner [" + script.id + "]: "
                        + safePreview(best.result.fullText));
                return best.result;
            }

            Log.w(TAG, "OCR produced no usable text for " + script.id);
            return new OcrResult("", new ArrayList<>(), new ArrayList<>());
        } finally {
            for (PaddleRecEngine engine : engines.values()) {
                try {
                    engine.close();
                } catch (Exception ignored) {
                }
            }
            for (Bitmap crop : boxCrops) {
                if (crop != null && crop != upscaled && crop != bitmap && !crop.isRecycled()) {
                    crop.recycle();
                }
            }
            if (recycleUpscaled && !upscaled.isRecycled()) {
                upscaled.recycle();
            }
        }
    }

    private void ensureModels(Context context,
                              OcrScriptChoice script,
                              @Nullable PaddleOcrModelManager.ProgressCallback progress)
            throws Exception {
        if (script.usesPaddle()) {
            paddleModels.ensureDetModel(context, progress);
            paddleModels.ensurePacks(context, script.paddlePacks, progress);
        }
        if (script.usesTess() && script.tessLanguageCodes != null) {
            tessdataManager.ensureLanguageData(
                    context,
                    script.tessLanguageCodes,
                    (current, total, languageCode) -> {
                        if (progress != null) {
                            progress.onProgress(current, total, languageCode);
                        }
                    });
        }
    }

    private List<Bitmap> detectLineCrops(Context context, Bitmap image) {
        List<Bitmap> crops = new ArrayList<>();
        try (PaddleDetEngine det = PaddleDetEngine.open(context, paddleModels)) {
            List<PaddleDetEngine.TextBox> boxes = det.detect(image);
            for (PaddleDetEngine.TextBox box : boxes) {
                Bitmap crop = cropBox(image, box.bounds);
                if (crop != null) {
                    crops.add(crop);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Detection failed, falling back to full crop", e);
        }
        return crops;
    }

    @Nullable
    private static Bitmap cropBox(Bitmap source, Rect bounds) {
        int pad = Math.max(2, Math.min(bounds.width(), bounds.height()) / 20);
        int left = Math.max(0, bounds.left - pad);
        int top = Math.max(0, bounds.top - pad);
        int right = Math.min(source.getWidth(), bounds.right + pad);
        int bottom = Math.min(source.getHeight(), bounds.bottom + pad);
        int width = right - left;
        int height = bottom - top;
        if (width < 8 || height < 8) {
            return null;
        }
        Bitmap crop = Bitmap.createBitmap(source, left, top, width, height);
        if (height > width * 1.35f && height >= 24) {
            Bitmap rotated = rotate90(crop);
            if (rotated != crop && !crop.isRecycled()) {
                crop.recycle();
            }
            crop = rotated;
        }
        Bitmap padded = OcrImagePrep.padForRec(
                crop, Math.max(2, Math.min(crop.getWidth(), crop.getHeight()) / 16));
        if (padded != crop && !crop.isRecycled()) {
            crop.recycle();
        }
        return padded;
    }

    private static Bitmap rotate90(Bitmap source) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private ScoredOcr recognizeCrops(Context context,
                                     List<Bitmap> crops,
                                     Bitmap wholeImage,
                                     PaddleOcrModelManager.ScriptPack[] packs,
                                     Map<PaddleOcrModelManager.ScriptPack, PaddleRecEngine> engines) {
        List<String> bestLines = new ArrayList<>();
        float confSum = 0f;
        int confCount = 0;
        int scriptBonus = 0;
        PaddleOcrModelManager.ScriptPack dominantPack = null;
        Map<PaddleOcrModelManager.ScriptPack, Integer> packVotes = new HashMap<>();

        for (Bitmap crop : crops) {
            boolean alreadyLineCrop = crop != wholeImage;
            List<ScoredLine> bestForCrop = new ArrayList<>();
            float bestCropScore = Float.NEGATIVE_INFINITY;
            PaddleOcrModelManager.ScriptPack bestPack = null;

            for (PaddleOcrModelManager.ScriptPack pack : packs) {
                if (!paddleModels.hasPack(context, pack)) {
                    continue;
                }
                try {
                    PaddleRecEngine engine = engines.get(pack);
                    if (engine == null) {
                        engine = PaddleRecEngine.open(context, paddleModels, pack);
                        engines.put(pack, engine);
                    }
                    List<PaddleRecEngine.LineResult> results =
                            engine.recognizeImage(crop, alreadyLineCrop);
                    List<ScoredLine> scoredLines = new ArrayList<>();
                    float cropScore = 0f;
                    for (PaddleRecEngine.LineResult line : results) {
                        ScoredLine scored = scoreLine(line, pack);
                        if (scored.text.isEmpty()) {
                            continue;
                        }
                        scoredLines.add(scored);
                        cropScore += Math.max(0, scored.rank());
                    }
                    if (scoredLines.isEmpty()) {
                        continue;
                    }
                    if (cropScore > bestCropScore) {
                        bestCropScore = cropScore;
                        bestForCrop = scoredLines;
                        bestPack = pack;
                    }
                    if (scoredLines.size() == 1 && isStrongLine(scoredLines.get(0), pack)) {
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Rec failed for pack " + pack.id, e);
                }
            }

            if (bestForCrop.isEmpty()) {
                continue;
            }
            for (ScoredLine bestLine : bestForCrop) {
                Log.i(TAG, String.format(Locale.US, "line pack=%s conf=%.3f text=[%s]",
                        bestPack != null ? bestPack.id : "?",
                        bestLine.confidence,
                        safePreview(bestLine.text)));
                bestLines.add(bestLine.text);
                confSum += bestLine.confidence;
                confCount++;
                scriptBonus += bestLine.scriptBonus;
            }
            if (bestPack != null) {
                packVotes.put(bestPack, packVotes.getOrDefault(bestPack, 0) + 1);
            }
        }

        if (bestLines.isEmpty()) {
            return ScoredOcr.empty();
        }

        List<String> deduped = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : bestLines) {
            String key = line.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                deduped.add(line);
            }
        }

        int bestVotes = -1;
        for (Map.Entry<PaddleOcrModelManager.ScriptPack, Integer> entry : packVotes.entrySet()) {
            if (entry.getValue() > bestVotes) {
                bestVotes = entry.getValue();
                dominantPack = entry.getKey();
            }
        }

        OcrResult result = buildResultFromLines(deduped);
        float mean = confCount == 0 ? 0f : confSum / confCount;
        return new ScoredOcr(
                result,
                mean,
                scriptBonus + packScoreBonus(result.fullText, dominantPack),
                dominantPack);
    }

    private static ScoredLine scoreLine(PaddleRecEngine.LineResult line,
                                        PaddleOcrModelManager.ScriptPack pack) {
        String cleaned = cleanFragment(line.text);
        if (cleaned.isEmpty()) {
            return ScoredLine.empty();
        }
        return new ScoredLine(cleaned, line.confidence, packScoreBonus(cleaned, pack), pack);
    }

    private static boolean isStrongLine(ScoredLine line, PaddleOcrModelManager.ScriptPack pack) {
        if (line.text.isEmpty() || line.confidence < 0.55f) {
            return false;
        }
        switch (pack) {
            case ESLAV:
                return countScript(line.text, MultilingualOcrEngine::isCyrillic) >= 2;
            case ENGLISH:
            case LATIN:
                return countScript(line.text, MultilingualOcrEngine::isLatinLetter) >= 2;
            case GREEK:
                return countScript(line.text, MultilingualOcrEngine::isGreek) >= 2;
            case ARABIC:
                return countScript(line.text, MultilingualOcrEngine::isArabic) >= 2;
            case KOREAN:
                return countScript(line.text, MultilingualOcrEngine::isHangul) >= 1;
            case CHINESE:
                return countScript(line.text, MultilingualOcrEngine::isCjk) >= 1;
            default:
                return false;
        }
    }

    private static int qualityScore(ScoredOcr scored) {
        if (scored.result.isEmpty()) {
            return Integer.MIN_VALUE / 8;
        }
        String text = scored.result.fullText;
        int letters = countScript(text, Character::isLetter);
        int words = 0;
        int longWords = 0;
        for (String part : text.split("\\s+")) {
            int len = 0;
            for (int i = 0; i < part.length(); ) {
                int cp = part.codePointAt(i);
                if (Character.isLetter(cp)) {
                    len++;
                }
                i += Character.charCount(cp);
            }
            if (len >= 2) {
                words++;
            }
            if (len >= 5) {
                longWords++;
            }
        }
        int junkPenalty = 0;
        for (String line : scored.result.lines) {
            int lineLetters = countScript(line, Character::isLetter);
            if (lineLetters > 0 && lineLetters <= 2) {
                junkPenalty += 8;
            }
        }
        return Math.round(scored.meanConfidence * 80f)
                + scored.scriptBonus
                + letters
                + words * 10
                + longWords * 12
                - junkPenalty;
    }

    private ScoredOcr tryTessForScript(Context context,
                                       Bitmap fullImage,
                                       OcrScriptChoice script,
                                       @Nullable PaddleOcrModelManager.ProgressCallback progress) {
        if (script.tessInitLangs == null) {
            return ScoredOcr.empty();
        }
        try {
            String dataPath = tessdataManager.getTessParentDir(context).getAbsolutePath();
            ScoredOcr best = ScoredOcr.empty();
            ScriptFamily family = familyFor(script);

            List<Bitmap> variants = new ArrayList<>();
            variants.add(OcrImagePrep.contrastStretchBrightInk(fullImage));
            variants.add(OcrImagePrep.contrastStretchLuma(fullImage));
            variants.add(OcrImagePrep.contrastStretch(fullImage));
            variants.add(OcrImagePrep.binarize(fullImage));

            String[] langAttempts;
            if (script == OcrScriptChoice.CYRILLIC) {
                langAttempts = new String[]{"rus", "rus+eng", "ukr", "ukr+eng"};
            } else {
                langAttempts = new String[]{script.tessInitLangs};
            }

            try {
                for (Bitmap variant : variants) {
                    for (String langs : langAttempts) {
                        ScoredOcr attempt = recognizeWithTess(
                                dataPath, langs, variant, family, true);
                        best = preferBetter(best, attempt);
                    }
                }
            } finally {
                for (Bitmap variant : variants) {
                    if (variant != fullImage && !variant.isRecycled()) {
                        variant.recycle();
                    }
                }
            }
            Log.i(TAG, "Tess [" + script.id + "] best: " + safePreview(best.result.fullText)
                    + " q=" + qualityScore(best));
            return best;
        } catch (Exception e) {
            Log.e(TAG, "Tess fallback failed for " + script.id, e);
            return ScoredOcr.empty();
        }
    }

    private static ScriptFamily familyFor(OcrScriptChoice script) {
        switch (script) {
            case SEMITIC:
                return ScriptFamily.HEBREW;
            case ARMENIAN:
                return ScriptFamily.ARMENIAN;
            case GEORGIAN:
                return ScriptFamily.GEORGIAN;
            case CYRILLIC:
            default:
                return ScriptFamily.CYRILLIC;
        }
    }

    private enum ScriptFamily {
        CYRILLIC, HEBREW, ARMENIAN, GEORGIAN
    }

    private static ScoredOcr recognizeWithTess(String dataPath,
                                               String langs,
                                               Bitmap prepared,
                                               ScriptFamily family,
                                               boolean fullBlock) {
        TessBaseAPI tess = new TessBaseAPI();
        try {
            if (!tess.init(dataPath, langs, TessBaseAPI.OEM_LSTM_ONLY)) {
                return ScoredOcr.empty();
            }
            tess.setPageSegMode(fullBlock
                    ? TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                    : TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);
            tess.setVariable("preserve_interword_spaces", "1");
            tess.setImage(prepared);
            OcrResult result = buildResultFromLines(
                    filterJunkLines(splitAndCleanLines(tess.getUTF8Text())));
            if (result.isEmpty()) {
                return ScoredOcr.empty();
            }

            int targetCount;
            switch (family) {
                case HEBREW:
                    targetCount = countScript(result.fullText, MultilingualOcrEngine::isHebrew);
                    break;
                case ARMENIAN:
                    targetCount = countScript(result.fullText, MultilingualOcrEngine::isArmenian);
                    break;
                case GEORGIAN:
                    targetCount = countScript(result.fullText, MultilingualOcrEngine::isGeorgian);
                    break;
                case CYRILLIC:
                default:
                    targetCount = countScript(result.fullText, MultilingualOcrEngine::isCyrillic);
                    break;
            }
            int letters = countScript(result.fullText, Character::isLetter);
            if (letters >= 3 && targetCount * 2 < letters) {
                Log.w(TAG, "Tess rejected weak " + family + ": " + safePreview(result.fullText));
                return ScoredOcr.empty();
            }
            int bonus = targetCount * 35;
            float conf = 0.55f + Math.min(0.35f, targetCount / 40f);
            return new ScoredOcr(result, conf, bonus, null);
        } finally {
            tess.recycle();
        }
    }

    private static ScoredOcr preferBetter(ScoredOcr current, ScoredOcr candidate) {
        return qualityScore(candidate) > qualityScore(current) ? candidate : current;
    }

    private static int packScoreBonus(@Nullable String text,
                                      @Nullable PaddleOcrModelManager.ScriptPack pack) {
        if (text == null || text.isEmpty() || pack == null) {
            return 0;
        }
        switch (pack) {
            case ESLAV:
                return countScript(text, MultilingualOcrEngine::isCyrillic) * 40;
            case ENGLISH:
            case LATIN:
                return countScript(text, MultilingualOcrEngine::isLatinLetter);
            case GREEK:
                return countScript(text, MultilingualOcrEngine::isGreek) * 30;
            case ARABIC:
                return countScript(text, MultilingualOcrEngine::isArabic) * 30;
            case KOREAN:
                return countScript(text, MultilingualOcrEngine::isHangul) * 30;
            case CHINESE:
                return countScript(text, MultilingualOcrEngine::isCjk) * 30;
            default:
                return 0;
        }
    }

    private static int countScript(@Nullable String text, java.util.function.IntPredicate matcher) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (matcher.test(cp)) {
                count++;
            }
            i += Character.charCount(cp);
        }
        return count;
    }

    private static boolean isCyrillic(int cp) {
        return (cp >= 0x0400 && cp <= 0x04FF) || (cp >= 0x0500 && cp <= 0x052F);
    }

    private static boolean isLatinLetter(int cp) {
        return (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')
                || (cp >= 0x00C0 && cp <= 0x024F);
    }

    private static boolean isArmenian(int cp) {
        return cp >= 0x0530 && cp <= 0x058F;
    }

    private static boolean isGeorgian(int cp) {
        return cp >= 0x10A0 && cp <= 0x10FF;
    }

    private static boolean isGreek(int cp) {
        return (cp >= 0x0370 && cp <= 0x03FF) || (cp >= 0x1F00 && cp <= 0x1FFF);
    }

    private static boolean isHangul(int cp) {
        return cp >= 0xAC00 && cp <= 0xD7AF;
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0x3400 && cp <= 0x9FFF)
                || (cp >= 0xAC00 && cp <= 0xD7AF)
                || (cp >= 0xF900 && cp <= 0xFAFF);
    }

    private static boolean isArabic(int cp) {
        return (cp >= 0x0600 && cp <= 0x06FF)
                || (cp >= 0x0750 && cp <= 0x077F)
                || (cp >= 0x08A0 && cp <= 0x08FF)
                || (cp >= 0xFB50 && cp <= 0xFDFF)
                || (cp >= 0xFE70 && cp <= 0xFEFF);
    }

    private static boolean isHebrew(int cp) {
        return (cp >= 0x0590 && cp <= 0x05FF) || (cp >= 0xFB1D && cp <= 0xFB4F);
    }

    private static ScoredOcr sanitizeResult(ScoredOcr scored) {
        if (scored.result.isEmpty()) {
            return scored;
        }
        List<String> cleaned = filterJunkLines(scored.result.lines);
        if (cleaned.isEmpty()) {
            return ScoredOcr.empty();
        }
        if (cleaned.size() == scored.result.lines.size()
                && cleaned.equals(scored.result.lines)) {
            return scored;
        }
        OcrResult result = buildResultFromLines(cleaned);
        return new ScoredOcr(result, scored.meanConfidence, scored.scriptBonus, scored.pack);
    }

    private static List<String> filterJunkLines(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String cleaned = cleanFragment(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            int letters = countScript(cleaned, Character::isLetter);
            if (letters < 3) {
                continue;
            }
            int nonSpace = 0;
            for (int i = 0; i < cleaned.length(); ) {
                int cp = cleaned.codePointAt(i);
                if (!Character.isWhitespace(cp)) {
                    nonSpace++;
                }
                i += Character.charCount(cp);
            }
            if (letters * 2 < nonSpace) {
                continue;
            }
            out.add(cleaned);
        }
        return out;
    }

    private static OcrResult buildResultFromLines(List<String> lines) {
        List<String> filtered = filterJunkLines(lines);
        String fullText = joinAsSingleField(filtered);
        Set<String> options = new LinkedHashSet<>();
        if (!fullText.isEmpty()) {
            options.add(fullText);
        }
        if (filtered.size() > 1) {
            options.addAll(filtered);
        }
        return new OcrResult(fullText, filtered, new ArrayList<>(options));
    }

    private static List<String> splitAndCleanLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String part : text.split("\\R")) {
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
        cleaned = cleaned.replaceAll("^[|_=\\-•·]+", "");
        cleaned = cleaned.replaceAll("[|_=\\-•·]+$", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static String safePreview(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    private static final class ScoredLine {
        final String text;
        final float confidence;
        final int scriptBonus;
        @Nullable
        final PaddleOcrModelManager.ScriptPack pack;

        ScoredLine(String text,
                   float confidence,
                   int scriptBonus,
                   @Nullable PaddleOcrModelManager.ScriptPack pack) {
            this.text = text;
            this.confidence = confidence;
            this.scriptBonus = scriptBonus;
            this.pack = pack;
        }

        static ScoredLine empty() {
            return new ScoredLine("", 0f, 0, null);
        }

        int rank() {
            if (text.isEmpty()) {
                return Integer.MIN_VALUE / 4;
            }
            int letters = countScript(text, Character::isLetter);
            return Math.round(confidence * 100f) + scriptBonus + letters;
        }
    }

    private static final class ScoredOcr {
        final OcrResult result;
        final float meanConfidence;
        final int scriptBonus;
        @Nullable
        final PaddleOcrModelManager.ScriptPack pack;

        ScoredOcr(OcrResult result,
                  float meanConfidence,
                  int scriptBonus,
                  @Nullable PaddleOcrModelManager.ScriptPack pack) {
            this.result = result;
            this.meanConfidence = meanConfidence;
            this.scriptBonus = scriptBonus;
            this.pack = pack;
        }

        static ScoredOcr empty() {
            return new ScoredOcr(new OcrResult("", new ArrayList<>(), new ArrayList<>()), 0f, 0, null);
        }
    }
}
