package com.vlad.homelibrary.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.vlad.homelibrary.R;

public enum OcrScriptChoice {

    LATIN(
            "latin",
            R.string.ocr_script_latin,
            new PaddleOcrModelManager.ScriptPack[]{
                    PaddleOcrModelManager.ScriptPack.ENGLISH,
                    PaddleOcrModelManager.ScriptPack.LATIN
            },
            null,
            null
    ),
    CYRILLIC(
            "cyrillic",
            R.string.ocr_script_cyrillic,
            new PaddleOcrModelManager.ScriptPack[]{PaddleOcrModelManager.ScriptPack.ESLAV},
            TessdataManager.CYRILLIC_LANGUAGE_CODES,
            "rus+ukr+eng"
    ),
    SEMITIC(
            "semitic",
            R.string.ocr_script_semitic,
            new PaddleOcrModelManager.ScriptPack[]{PaddleOcrModelManager.ScriptPack.ARABIC},
            new String[]{"heb", "eng"},
            "heb"
    ),
    PERSIAN(
            "persian",
            R.string.ocr_script_persian,
            new PaddleOcrModelManager.ScriptPack[]{PaddleOcrModelManager.ScriptPack.ARABIC},
            null,
            null
    ),
    ARMENIAN(
            "armenian",
            R.string.ocr_script_armenian,
            null,
            new String[]{"hye", "eng"},
            "hye"
    ),
    GEORGIAN(
            "georgian",
            R.string.ocr_script_georgian,
            null,
            new String[]{"kat", "eng"},
            "kat"
    ),
    GREEK(
            "greek",
            R.string.ocr_script_greek,
            new PaddleOcrModelManager.ScriptPack[]{PaddleOcrModelManager.ScriptPack.GREEK},
            null,
            null
    ),
    JCK(
            "jck",
            R.string.ocr_script_jck,
            new PaddleOcrModelManager.ScriptPack[]{
                    PaddleOcrModelManager.ScriptPack.CHINESE,
                    PaddleOcrModelManager.ScriptPack.KOREAN
            },
            null,
            null
    );

    public final String id;
    @StringRes
    public final int labelRes;
    @Nullable
    public final PaddleOcrModelManager.ScriptPack[] paddlePacks;
    @Nullable
    public final String[] tessLanguageCodes;
    @Nullable
    public final String tessInitLangs;

    OcrScriptChoice(String id,
                    @StringRes int labelRes,
                    @Nullable PaddleOcrModelManager.ScriptPack[] paddlePacks,
                    @Nullable String[] tessLanguageCodes,
                    @Nullable String tessInitLangs) {
        this.id = id;
        this.labelRes = labelRes;
        this.paddlePacks = paddlePacks;
        this.tessLanguageCodes = tessLanguageCodes;
        this.tessInitLangs = tessInitLangs;
    }

    public boolean usesPaddle() {
        return paddlePacks != null && paddlePacks.length > 0;
    }

    public boolean usesTess() {
        return tessLanguageCodes != null && tessLanguageCodes.length > 0;
    }

    @NonNull
    public static OcrScriptChoice fromId(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return CYRILLIC;
        }
        for (OcrScriptChoice choice : values()) {
            if (choice.id.equalsIgnoreCase(id)) {
                return choice;
            }
        }
        return CYRILLIC;
    }

    @NonNull
    public static OcrScriptChoice defaultForDeviceLocale() {
        String lang = java.util.Locale.getDefault().getLanguage();
        if ("ru".equals(lang) || "uk".equals(lang) || "be".equals(lang)
                || "bg".equals(lang) || "sr".equals(lang) || "mk".equals(lang)) {
            return CYRILLIC;
        }
        if ("el".equals(lang)) {
            return GREEK;
        }
        if ("ar".equals(lang) || "he".equals(lang) || "iw".equals(lang)) {
            return SEMITIC;
        }
        if ("fa".equals(lang) || "ur".equals(lang)) {
            return PERSIAN;
        }
        if ("hy".equals(lang)) {
            return ARMENIAN;
        }
        if ("ka".equals(lang)) {
            return GEORGIAN;
        }
        if ("zh".equals(lang) || "ja".equals(lang) || "ko".equals(lang)) {
            return JCK;
        }
        return LATIN;
    }
}
