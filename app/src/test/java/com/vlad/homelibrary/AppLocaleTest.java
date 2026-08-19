package com.vlad.homelibrary;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppLocaleTest {

    private static final String[] TAGS = {
            AppLocale.SYSTEM,
            AppLocale.ENGLISH,
            AppLocale.SPANISH,
            AppLocale.RUSSIAN,
            AppLocale.BELARUSIAN,
    };

    @Test
    public void languageOnlyKeepsIsoLanguage() {
        assertEquals("", AppLocale.languageOnly(null));
        assertEquals("", AppLocale.languageOnly(""));
        assertEquals("en", AppLocale.languageOnly("en"));
        assertEquals("es", AppLocale.languageOnly("es-ES"));
        assertEquals("be", AppLocale.languageOnly("be-BY"));
        assertEquals("ru", AppLocale.languageOnly("RU"));
    }

    @Test
    public void indexOfMatchesSupportedLanguageOrFallsBackToSystem() {
        assertEquals(0, AppLocale.indexOf(null, TAGS));
        assertEquals(0, AppLocale.indexOf("", TAGS));
        assertEquals(1, AppLocale.indexOf("en", TAGS));
        assertEquals(2, AppLocale.indexOf("es-MX", TAGS));
        assertEquals(3, AppLocale.indexOf("ru-RU", TAGS));
        assertEquals(4, AppLocale.indexOf("be", TAGS));
        assertEquals(0, AppLocale.indexOf("fr", TAGS));
    }
}
