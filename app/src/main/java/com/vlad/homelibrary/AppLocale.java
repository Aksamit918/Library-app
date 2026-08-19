package com.vlad.homelibrary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class AppLocale {

    public static final String SYSTEM = "";
    public static final String ENGLISH = "en";
    public static final String SPANISH = "es";
    public static final String RUSSIAN = "ru";
    public static final String BELARUSIAN = "be";

    private AppLocale() {
    }

    @NonNull
    public static Option[] options() {
        return new Option[]{
                new Option(SYSTEM, R.string.locale_system_default),
                new Option(ENGLISH, R.string.locale_english),
                new Option(SPANISH, R.string.locale_spanish),
                new Option(RUSSIAN, R.string.locale_russian),
                new Option(BELARUSIAN, R.string.locale_belarusian),
        };
    }

    @NonNull
    public static String currentTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) {
            return SYSTEM;
        }
        java.util.Locale locale = locales.get(0);
        if (locale == null) {
            return SYSTEM;
        }
        String language = locale.getLanguage();
        return language == null ? SYSTEM : language;
    }

    public static int selectedIndex() {
        return indexOf(currentTag(), optionTags());
    }

    public static void apply(@Nullable String tag) {
        if (tag == null || tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
            return;
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageOnly(tag)));
    }

    @NonNull
    static String languageOnly(@Nullable String tag) {
        if (tag == null || tag.isEmpty()) {
            return SYSTEM;
        }
        int separator = tag.indexOf('-');
        String language = separator < 0 ? tag : tag.substring(0, separator);
        return language.toLowerCase(java.util.Locale.ROOT);
    }

    static int indexOf(@Nullable String tag, @NonNull String[] optionTags) {
        String language = languageOnly(tag);
        for (int i = 0; i < optionTags.length; i++) {
            if (optionTags[i].equals(language)) {
                return i;
            }
        }
        return 0;
    }

    @NonNull
    private static String[] optionTags() {
        Option[] options = options();
        String[] tags = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            tags[i] = options[i].tag;
        }
        return tags;
    }

    public static final class Option {
        public final String tag;
        public final int labelRes;

        Option(@NonNull String tag, int labelRes) {
            this.tag = tag;
            this.labelRes = labelRes;
        }
    }
}
