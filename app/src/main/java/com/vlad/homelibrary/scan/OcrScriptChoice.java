package com.vlad.homelibrary.scan;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.vlad.homelibrary.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum OcrScriptChoice {

    RUS("rus", R.string.ocr_lang_rus, Family.CYRILLIC, MlKitScript.NONE),
    BEL("bel", R.string.ocr_lang_bel, Family.CYRILLIC, MlKitScript.NONE),
    UKR("ukr", R.string.ocr_lang_ukr, Family.CYRILLIC, MlKitScript.NONE),
    BUL("bul", R.string.ocr_lang_bul, Family.CYRILLIC, MlKitScript.NONE),
    SRP("srp", R.string.ocr_lang_srp, Family.CYRILLIC, MlKitScript.NONE),
    MKD("mkd", R.string.ocr_lang_mkd, Family.CYRILLIC, MlKitScript.NONE),
    KAZ("kaz", R.string.ocr_lang_kaz, Family.CYRILLIC, MlKitScript.NONE),
    KIR("kir", R.string.ocr_lang_kir, Family.CYRILLIC, MlKitScript.NONE),
    TGK("tgk", R.string.ocr_lang_tgk, Family.CYRILLIC, MlKitScript.NONE),
    TAT("tat", R.string.ocr_lang_tat, Family.CYRILLIC, MlKitScript.NONE),
    AZE_CYRL("aze_cyrl", R.string.ocr_lang_aze_cyrl, Family.CYRILLIC, MlKitScript.NONE),
    UZB_CYRL("uzb_cyrl", R.string.ocr_lang_uzb_cyrl, Family.CYRILLIC, MlKitScript.NONE),
    MON("mon", R.string.ocr_lang_mon, Family.CYRILLIC, MlKitScript.NONE),

    ENG("eng", R.string.ocr_lang_eng, Family.LATIN, MlKitScript.NONE),
    DEU("deu", R.string.ocr_lang_deu, Family.LATIN, MlKitScript.NONE),
    FRA("fra", R.string.ocr_lang_fra, Family.LATIN, MlKitScript.NONE),
    SPA("spa", R.string.ocr_lang_spa, Family.LATIN, MlKitScript.NONE),
    ITA("ita", R.string.ocr_lang_ita, Family.LATIN, MlKitScript.NONE),
    POR("por", R.string.ocr_lang_por, Family.LATIN, MlKitScript.NONE),
    POL("pol", R.string.ocr_lang_pol, Family.LATIN, MlKitScript.NONE),
    NLD("nld", R.string.ocr_lang_nld, Family.LATIN, MlKitScript.NONE),
    CES("ces", R.string.ocr_lang_ces, Family.LATIN, MlKitScript.NONE),
    SLK("slk", R.string.ocr_lang_slk, Family.LATIN, MlKitScript.NONE),
    HUN("hun", R.string.ocr_lang_hun, Family.LATIN, MlKitScript.NONE),
    RON("ron", R.string.ocr_lang_ron, Family.LATIN, MlKitScript.NONE),
    SWE("swe", R.string.ocr_lang_swe, Family.LATIN, MlKitScript.NONE),
    NOR("nor", R.string.ocr_lang_nor, Family.LATIN, MlKitScript.NONE),
    DAN("dan", R.string.ocr_lang_dan, Family.LATIN, MlKitScript.NONE),
    FIN("fin", R.string.ocr_lang_fin, Family.LATIN, MlKitScript.NONE),
    EST("est", R.string.ocr_lang_est, Family.LATIN, MlKitScript.NONE),
    LAV("lav", R.string.ocr_lang_lav, Family.LATIN, MlKitScript.NONE),
    LIT("lit", R.string.ocr_lang_lit, Family.LATIN, MlKitScript.NONE),
    SLV("slv", R.string.ocr_lang_slv, Family.LATIN, MlKitScript.NONE),
    HRV("hrv", R.string.ocr_lang_hrv, Family.LATIN, MlKitScript.NONE),
    BOS("bos", R.string.ocr_lang_bos, Family.LATIN, MlKitScript.NONE),
    SRP_LATN("srp_latn", R.string.ocr_lang_srp_latn, Family.LATIN, MlKitScript.NONE),
    CAT("cat", R.string.ocr_lang_cat, Family.LATIN, MlKitScript.NONE),
    GLG("glg", R.string.ocr_lang_glg, Family.LATIN, MlKitScript.NONE),
    EUS("eus", R.string.ocr_lang_eus, Family.LATIN, MlKitScript.NONE),
    TUR("tur", R.string.ocr_lang_tur, Family.LATIN, MlKitScript.NONE),
    AZE("aze", R.string.ocr_lang_aze, Family.LATIN, MlKitScript.NONE),
    UZB("uzb", R.string.ocr_lang_uzb, Family.LATIN, MlKitScript.NONE),
    VIE("vie", R.string.ocr_lang_vie, Family.LATIN, MlKitScript.NONE),
    IND("ind", R.string.ocr_lang_ind, Family.LATIN, MlKitScript.NONE),
    MSA("msa", R.string.ocr_lang_msa, Family.LATIN, MlKitScript.NONE),
    SWA("swa", R.string.ocr_lang_swa, Family.LATIN, MlKitScript.NONE),
    AFR("afr", R.string.ocr_lang_afr, Family.LATIN, MlKitScript.NONE),
    ISL("isl", R.string.ocr_lang_isl, Family.LATIN, MlKitScript.NONE),
    GLE("gle", R.string.ocr_lang_gle, Family.LATIN, MlKitScript.NONE),
    GLA("gla", R.string.ocr_lang_gla, Family.LATIN, MlKitScript.NONE),
    CYM("cym", R.string.ocr_lang_cym, Family.LATIN, MlKitScript.NONE),
    MLT("mlt", R.string.ocr_lang_mlt, Family.LATIN, MlKitScript.NONE),
    SQI("sqi", R.string.ocr_lang_sqi, Family.LATIN, MlKitScript.NONE),
    EPO("epo", R.string.ocr_lang_epo, Family.LATIN, MlKitScript.NONE),
    LAT("lat", R.string.ocr_lang_lat, Family.LATIN, MlKitScript.NONE),
    FIL("fil", R.string.ocr_lang_fil, Family.LATIN, MlKitScript.NONE),
    HAT("hat", R.string.ocr_lang_hat, Family.LATIN, MlKitScript.NONE),
    CEB("ceb", R.string.ocr_lang_ceb, Family.LATIN, MlKitScript.NONE),
    JAV("jav", R.string.ocr_lang_jav, Family.LATIN, MlKitScript.NONE),
    SUN("sun", R.string.ocr_lang_sun, Family.LATIN, MlKitScript.NONE),
    BRE("bre", R.string.ocr_lang_bre, Family.LATIN, MlKitScript.NONE),
    COS("cos", R.string.ocr_lang_cos, Family.LATIN, MlKitScript.NONE),
    FAO("fao", R.string.ocr_lang_fao, Family.LATIN, MlKitScript.NONE),
    FRY("fry", R.string.ocr_lang_fry, Family.LATIN, MlKitScript.NONE),
    KMR("kmr", R.string.ocr_lang_kmr, Family.LATIN, MlKitScript.NONE),
    LTZ("ltz", R.string.ocr_lang_ltz, Family.LATIN, MlKitScript.NONE),
    MRI("mri", R.string.ocr_lang_mri, Family.LATIN, MlKitScript.NONE),
    OCI("oci", R.string.ocr_lang_oci, Family.LATIN, MlKitScript.NONE),
    QUE("que", R.string.ocr_lang_que, Family.LATIN, MlKitScript.NONE),
    TON("ton", R.string.ocr_lang_ton, Family.LATIN, MlKitScript.NONE),
    YOR("yor", R.string.ocr_lang_yor, Family.LATIN, MlKitScript.NONE),

    ELL("ell", R.string.ocr_lang_ell, Family.GREEK, MlKitScript.NONE),
    GRC("grc", R.string.ocr_lang_grc, Family.GREEK, MlKitScript.NONE),
    HYE("hye", R.string.ocr_lang_hye, Family.ARMENIAN, MlKitScript.NONE),
    KAT("kat", R.string.ocr_lang_kat, Family.GEORGIAN, MlKitScript.NONE),

    ARA("ara", R.string.ocr_lang_ara, Family.ARABIC, MlKitScript.NONE),
    FAS("fas", R.string.ocr_lang_fas, Family.ARABIC, MlKitScript.NONE),
    URD("urd", R.string.ocr_lang_urd, Family.ARABIC, MlKitScript.NONE),
    PUS("pus", R.string.ocr_lang_pus, Family.ARABIC, MlKitScript.NONE),
    UIG("uig", R.string.ocr_lang_uig, Family.ARABIC, MlKitScript.NONE),
    SND("snd", R.string.ocr_lang_snd, Family.ARABIC, MlKitScript.NONE),
    HEB("heb", R.string.ocr_lang_heb, Family.HEBREW, MlKitScript.NONE),
    YID("yid", R.string.ocr_lang_yid, Family.HEBREW, MlKitScript.NONE),

    CHI_SIM("chi_sim", R.string.ocr_lang_chi_sim, Family.CJK, MlKitScript.CHINESE),
    CHI_TRA("chi_tra", R.string.ocr_lang_chi_tra, Family.CJK, MlKitScript.CHINESE),
    JPN("jpn", R.string.ocr_lang_jpn, Family.CJK, MlKitScript.JAPANESE),
    KOR("kor", R.string.ocr_lang_kor, Family.CJK, MlKitScript.KOREAN),

    HIN("hin", R.string.ocr_lang_hin, Family.INDIC, MlKitScript.NONE),
    BEN("ben", R.string.ocr_lang_ben, Family.INDIC, MlKitScript.NONE),
    TAM("tam", R.string.ocr_lang_tam, Family.INDIC, MlKitScript.NONE),
    TEL("tel", R.string.ocr_lang_tel, Family.INDIC, MlKitScript.NONE),
    KAN("kan", R.string.ocr_lang_kan, Family.INDIC, MlKitScript.NONE),
    MAL("mal", R.string.ocr_lang_mal, Family.INDIC, MlKitScript.NONE),
    GUJ("guj", R.string.ocr_lang_guj, Family.INDIC, MlKitScript.NONE),
    PAN("pan", R.string.ocr_lang_pan, Family.INDIC, MlKitScript.NONE),
    ORI("ori", R.string.ocr_lang_ori, Family.INDIC, MlKitScript.NONE),
    SIN("sin", R.string.ocr_lang_sin, Family.INDIC, MlKitScript.NONE),
    NEP("nep", R.string.ocr_lang_nep, Family.INDIC, MlKitScript.NONE),
    MAR("mar", R.string.ocr_lang_mar, Family.INDIC, MlKitScript.NONE),
    SAN("san", R.string.ocr_lang_san, Family.INDIC, MlKitScript.NONE),
    ASM("asm", R.string.ocr_lang_asm, Family.INDIC, MlKitScript.NONE),

    THA("tha", R.string.ocr_lang_tha, Family.OTHER, MlKitScript.NONE),
    LAO("lao", R.string.ocr_lang_lao, Family.OTHER, MlKitScript.NONE),
    KHM("khm", R.string.ocr_lang_khm, Family.OTHER, MlKitScript.NONE),
    MYA("mya", R.string.ocr_lang_mya, Family.OTHER, MlKitScript.NONE),
    AMH("amh", R.string.ocr_lang_amh, Family.OTHER, MlKitScript.NONE),
    TIR("tir", R.string.ocr_lang_tir, Family.OTHER, MlKitScript.NONE),
    BOD("bod", R.string.ocr_lang_bod, Family.OTHER, MlKitScript.NONE),
    DZO("dzo", R.string.ocr_lang_dzo, Family.OTHER, MlKitScript.NONE),
    CHR("chr", R.string.ocr_lang_chr, Family.OTHER, MlKitScript.NONE);

    public enum Family {
        CYRILLIC(R.string.ocr_script_cyrillic),
        LATIN(R.string.ocr_script_latin),
        GREEK(R.string.ocr_script_greek),
        ARMENIAN(R.string.ocr_script_armenian),
        GEORGIAN(R.string.ocr_script_georgian),
        ARABIC(R.string.ocr_script_arabic),
        HEBREW(R.string.ocr_script_hebrew),
        CJK(R.string.ocr_script_jck),
        INDIC(R.string.ocr_script_indic),
        OTHER(R.string.ocr_script_other);

        @StringRes
        public final int labelRes;

        Family(@StringRes int labelRes) {
            this.labelRes = labelRes;
        }
    }

    public enum MlKitScript {
        NONE, LATIN, CHINESE, JAPANESE, KOREAN
    }

    public final String id;
    @StringRes
    public final int labelRes;
    @NonNull
    public final Family family;
    @NonNull
    public final MlKitScript mlKitScript;
    @Nullable
    public final String[] tessLanguageCodes;
    @Nullable
    public final String tessInitLangs;
    @Nullable
    private String cachedIso6391;

    OcrScriptChoice(String id,
                    @StringRes int labelRes,
                    @NonNull Family family,
                    @NonNull MlKitScript mlKitScript) {
        this.id = id;
        this.labelRes = labelRes;
        this.family = family;
        this.mlKitScript = mlKitScript;
        this.tessLanguageCodes = new String[]{id};
        this.tessInitLangs = id;
    }

    public boolean usesMlKit() {
        return mlKitScript != MlKitScript.NONE;
    }

    public boolean usesTess() {
        return tessLanguageCodes != null && tessLanguageCodes.length > 0;
    }

    @NonNull
    public String tessBaseId() {
        if (id.endsWith("_cyrl") || id.endsWith("_latn")) {
            return id.substring(0, id.length() - 5);
        }
        return id;
    }

    @NonNull
    public String iso6391() {
        if (cachedIso6391 == null) {
            cachedIso6391 = computeIso6391();
        }
        return cachedIso6391;
    }

    @NonNull
    private String computeIso6391() {
        if (id.startsWith("chi_")) {
            return "zh";
        }
        if ("fil".equals(id)) {
            return "tl";
        }
        if ("nor".equals(id)) {
            return "nb";
        }
        String base = tessBaseId();
        for (java.util.Locale loc : java.util.Locale.getAvailableLocales()) {
            try {
                if (base.equalsIgnoreCase(loc.getISO3Language()) && loc.getLanguage().length() == 2) {
                    return loc.getLanguage();
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    @NonNull
    public String englishLabel(@NonNull Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(java.util.Locale.ENGLISH);
        return context.createConfigurationContext(config).getResources().getString(labelRes);
    }

    public boolean matchesQuery(@NonNull String query, @NonNull String localizedName,
                                @NonNull String englishName) {
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        if (id.toLowerCase(java.util.Locale.ROOT).startsWith(needle)
                || tessBaseId().toLowerCase(java.util.Locale.ROOT).startsWith(needle)
                || iso6391().startsWith(needle)) {
            return true;
        }
        return nameStartsWithQuery(localizedName, needle)
                || nameStartsWithQuery(englishName, needle);
    }

    private static boolean nameStartsWithQuery(@NonNull String name, @NonNull String query) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith(query)) {
            return true;
        }
        for (String part : lower.split("[\\s(/)\\-]+")) {
            if (!part.isEmpty() && part.startsWith(query)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static List<OcrScriptChoice> allSortedByLabel(@NonNull Context context) {
        List<OcrScriptChoice> out = new ArrayList<>(Arrays.asList(values()));
        Collator collator = Collator.getInstance(context.getResources().getConfiguration().getLocales().get(0));
        collator.setStrength(Collator.PRIMARY);
        out.sort((a, b) -> collator.compare(context.getString(a.labelRes), context.getString(b.labelRes)));
        return out;
    }

    @NonNull
    public static List<OcrScriptChoice> forFamily(@NonNull Family family) {
        List<OcrScriptChoice> out = new ArrayList<>();
        for (OcrScriptChoice choice : values()) {
            if (choice.family == family) {
                out.add(choice);
            }
        }
        return out;
    }

    @NonNull
    public static OcrScriptChoice fromId(@Nullable String id) {
        if (id == null || id.isEmpty() || isLegacyFamilyId(id)) {
            return defaultForDeviceLocale();
        }
        for (OcrScriptChoice choice : values()) {
            if (choice.id.equalsIgnoreCase(id)) {
                return choice;
            }
        }
        return defaultForDeviceLocale();
    }

    public static boolean isLegacyFamilyId(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return "cyrillic".equalsIgnoreCase(id)
                || "latin".equalsIgnoreCase(id)
                || "semitic".equalsIgnoreCase(id)
                || "persian".equalsIgnoreCase(id)
                || "jck".equalsIgnoreCase(id)
                || "armenian".equalsIgnoreCase(id)
                || "georgian".equalsIgnoreCase(id)
                || "greek".equalsIgnoreCase(id)
                || "indic".equalsIgnoreCase(id)
                || "other".equalsIgnoreCase(id)
                || "arabic".equalsIgnoreCase(id)
                || "hebrew".equalsIgnoreCase(id);
    }

    @NonNull
    public static OcrScriptChoice defaultForAppLocale(@NonNull Context context) {
        Configuration config = context.getResources().getConfiguration();
        return bestForLocale(config.getLocales().get(0));
    }

    @NonNull
    public static OcrScriptChoice defaultForDeviceLocale() {
        return bestForLocale(java.util.Locale.getDefault());
    }

    @NonNull
    static OcrScriptChoice bestForLocale(@NonNull java.util.Locale locale) {
        String lang = locale.getLanguage();
        String iso3 = "";
        try {
            iso3 = locale.getISO3Language();
        } catch (Exception ignored) {
        }
        String script = locale.getScript();
        String country = locale.getCountry();
        OcrScriptChoice best = ENG;
        int bestScore = 0;
        for (OcrScriptChoice choice : values()) {
            int score = choice.localeScore(lang, iso3, script, country);
            if (score > bestScore) {
                bestScore = score;
                best = choice;
            }
        }
        return best;
    }

    private int localeScore(@NonNull String lang,
                            @NonNull String iso3,
                            @NonNull String script,
                            @NonNull String country) {
        int score = 0;
        String base = tessBaseId();
        if (id.equalsIgnoreCase(iso3) || base.equalsIgnoreCase(iso3)) {
            score += 12;
        }
        if (id.equalsIgnoreCase(lang) || base.equalsIgnoreCase(lang) || iso6391().equalsIgnoreCase(lang)) {
            score += 10;
        }
        if ("zh".equals(lang) && (id.equals("chi_sim") || id.equals("chi_tra"))) {
            score += 12;
        }
        if ("nb".equals(lang) || "nn".equals(lang) || "no".equals(lang)) {
            if (id.equals("nor")) {
                score += 12;
            }
        }
        if ("iw".equals(lang) && id.equals("heb")) {
            score += 12;
        }
        if ("in".equals(lang) && id.equals("ind")) {
            score += 12;
        }
        if ("tl".equals(lang) && id.equals("fil")) {
            score += 10;
        }
        if (score == 0) {
            return 0;
        }
        if (id.endsWith("_cyrl")) {
            score += "Cyrl".equals(script) ? 6 : ("Latn".equals(script) ? -8 : -1);
        } else if (id.endsWith("_latn")) {
            score += "Latn".equals(script) ? 6 : ("Cyrl".equals(script) ? -8 : -1);
        } else if (id.equals("srp") && "sr".equals(lang)) {
            score += "Cyrl".equals(script) || script.isEmpty() ? 4 : -4;
        } else if (id.equals("aze") && "az".equals(lang)) {
            score += "Latn".equals(script) || script.isEmpty() ? 4 : -4;
        } else if (id.equals("uzb") && "uz".equals(lang)) {
            score += "Latn".equals(script) || script.isEmpty() ? 4 : -4;
        }
        if (id.equals("chi_tra")) {
            score += "TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country)
                    || "MO".equalsIgnoreCase(country) || "Hant".equals(script) ? 6 : -4;
        }
        if (id.equals("chi_sim")) {
            score += "CN".equalsIgnoreCase(country) || "SG".equalsIgnoreCase(country)
                    || "Hans".equals(script) || script.isEmpty() ? 4 : 0;
        }
        return score;
    }
}
