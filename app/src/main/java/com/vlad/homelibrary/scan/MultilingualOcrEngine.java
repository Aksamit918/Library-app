package com.vlad.homelibrary.scan;

import android.content.Context;
import android.graphics.Bitmap;
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
    private static final int LINE_MIN_SIDE_PX = 72;

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

        LocatedLine(String text, int top) {
            this.text = text;
            this.top = top;
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
                String cleaned = cleanFragment(line.getText());
                if (!isKeepableLine(cleaned)) {
                    continue;
                }
                int top = topOffset;
                if (line.getBoundingBox() != null) {
                    top += line.getBoundingBox().top;
                }
                out.add(new LocatedLine(cleaned, top));
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

            collectTessVariants(tess, image, 0, false, located);
            for (OcrImagePrep.TextBand band : bands) {
                Bitmap scaled = OcrImagePrep.fitForOcr(band.bitmap, LINE_MIN_SIDE_PX, OCR_MAX_SIDE_PX);
                try {
                    collectTessVariants(tess, scaled, band.top, true, located);
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
            int[] pageSegModes;
            if (singleLine) {
                pageSegModes = new int[]{
                        TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                        TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                };
            } else if (darkCover) {
                pageSegModes = new int[]{
                        TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT,
                        TessBaseAPI.PageSegMode.PSM_AUTO,
                        TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                };
            } else {
                pageSegModes = new int[]{
                        TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT,
                        TessBaseAPI.PageSegMode.PSM_AUTO
                };
            }
            int before = out.size();
            for (Bitmap variant : variants) {
                for (int psm : pageSegModes) {
                    collectTess(tess, variant, psm, topOffset, out);
                    if (singleLine && addedGoodLine(out, before)) {
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

    private static boolean addedGoodLine(List<LocatedLine> lines, int fromIndex) {
        for (int i = fromIndex; i < lines.size(); i++) {
            if (countAlnum(lines.get(i).text) >= 4) {
                return true;
            }
        }
        return false;
    }

    private static void collectTess(TessBaseAPI tess,
                                    Bitmap prepared,
                                    int pageSegMode,
                                    int topOffset,
                                    List<LocatedLine> out) {
        try {
            tess.setPageSegMode(pageSegMode);
            tess.setImage(prepared);
            int lineTop = topOffset;
            for (String part : splitAndCleanLines(tess.getUTF8Text())) {
                if (!isKeepableLine(part)) {
                    continue;
                }
                out.add(new LocatedLine(part, lineTop));
                lineTop += 8;
            }
        } catch (Exception e) {
            Log.e(TAG, "Tess page failed psm=" + pageSegMode, e);
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
        List<LocatedLine> sorted = new ArrayList<>(located);
        sorted.sort(Comparator
                .comparingInt((LocatedLine line) -> line.top)
                .thenComparingInt(line -> -line.text.length()));

        List<String> merged = new ArrayList<>();
        for (LocatedLine candidate : sorted) {
            int index = indexOfSimilar(merged, candidate.text);
            if (index < 0) {
                merged.add(candidate.text);
                continue;
            }
            if (countAlnum(candidate.text) > countAlnum(merged.get(index))) {
                merged.set(index, candidate.text);
            }
        }
        return buildResultFromLines(merged);
    }

    private static int indexOfSimilar(List<String> lines, String candidate) {
        String key = lettersKey(candidate);
        if (key.length() < 2) {
            return -1;
        }
        for (int i = 0; i < lines.size(); i++) {
            String other = lettersKey(lines.get(i));
            if (other.equals(key)) {
                return i;
            }
            String shorter = key.length() <= other.length() ? key : other;
            String longer = key.length() <= other.length() ? other : key;
            if (longer.contains(shorter)
                    && (shorter.length() * 100 >= longer.length() * 55
                    || longer.startsWith(shorter)
                    || longer.endsWith(shorter))) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private static String lettersKey(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetter(cp) || Character.isDigit(cp)) {
                builder.appendCodePoint(Character.toLowerCase(cp));
            }
            i += Character.charCount(cp);
        }
        return builder.toString();
    }

    private static int countAlnum(@Nullable String text) {
        return countScript(text, cp -> Character.isLetter(cp) || Character.isDigit(cp));
    }

    private static int countScript(@Nullable String text, IntPredicate matcher) {
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
        String fullText = filtered.isEmpty() ? "" : cleanFragment(String.join(" ", filtered));
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
            String cleaned = cleanFragment(line);
            if (isKeepableLine(cleaned)) {
                out.add(cleaned);
            }
        }
        return out;
    }

    private static boolean isKeepableLine(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) {
            return false;
        }
        int letters = countScript(cleaned, Character::isLetter);
        int digits = countScript(cleaned, Character::isDigit);
        return letters >= 2 || digits >= 3 || letters + digits >= 3;
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

    private static String cleanFragment(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        cleaned = cleaned.replace('\u00A0', ' ');
        cleaned = cleaned.replaceAll("^[|_=\\-•·«»\"'“”]+", "");
        cleaned = cleaned.replaceAll("[|_=\\-•·«»\"'“”]+$", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
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
