package com.vlad.homelibrary.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.vlad.homelibrary.R;

public enum OcrScriptChoice {

    LATIN(
            "latin",
            R.string.ocr_script_latin,
            MlKitScript.LATIN,
            new String[]{"Latin"},
            "Latin"
    ),
    CYRILLIC(
            "cyrillic",
            R.string.ocr_script_cyrillic,
            MlKitScript.NONE,
            new String[]{"Cyrillic"},
            "Cyrillic"
    ),
    SEMITIC(
            "semitic",
            R.string.ocr_script_semitic,
            MlKitScript.NONE,
            new String[]{"ara", "heb"},
            "ara+heb"
    ),
    PERSIAN(
            "persian",
            R.string.ocr_script_persian,
            MlKitScript.NONE,
            new String[]{"fas", "ara"},
            "fas+ara"
    ),
    ARMENIAN(
            "armenian",
            R.string.ocr_script_armenian,
            MlKitScript.NONE,
            new String[]{"hye", "eng"},
            "hye+eng"
    ),
    GEORGIAN(
            "georgian",
            R.string.ocr_script_georgian,
            MlKitScript.NONE,
            new String[]{"kat", "eng"},
            "kat+eng"
    ),
    GREEK(
            "greek",
            R.string.ocr_script_greek,
            MlKitScript.NONE,
            new String[]{"ell", "eng"},
            "ell+eng"
    ),
    JCK(
            "jck",
            R.string.ocr_script_jck,
            MlKitScript.CJK,
            null,
            null
    );

    public enum MlKitScript {
        NONE, LATIN, CJK
    }

    public final String id;
    @StringRes
    public final int labelRes;
    @NonNull
    public final MlKitScript mlKitScript;
    @Nullable
    public final String[] tessLanguageCodes;
    @Nullable
    public final String tessInitLangs;

    OcrScriptChoice(String id,
                    @StringRes int labelRes,
                    @NonNull MlKitScript mlKitScript,
                    @Nullable String[] tessLanguageCodes,
                    @Nullable String tessInitLangs) {
        this.id = id;
        this.labelRes = labelRes;
        this.mlKitScript = mlKitScript;
        this.tessLanguageCodes = tessLanguageCodes;
        this.tessInitLangs = tessInitLangs;
    }

    public boolean usesMlKit() {
        return mlKitScript != MlKitScript.NONE;
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
