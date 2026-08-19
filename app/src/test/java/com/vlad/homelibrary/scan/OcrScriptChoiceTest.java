package com.vlad.homelibrary.scan;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OcrScriptChoiceTest {

    @Test
    public void mapsExactLocalesToLanguagePacks() {
        assertEquals(OcrScriptChoice.BEL, OcrScriptChoice.bestForLocale(new Locale("be")));
        assertEquals(OcrScriptChoice.KAZ, OcrScriptChoice.bestForLocale(new Locale("kk")));
        assertEquals(OcrScriptChoice.KIR, OcrScriptChoice.bestForLocale(new Locale("ky")));
        assertEquals(OcrScriptChoice.LIT, OcrScriptChoice.bestForLocale(new Locale("lt")));
        assertEquals(OcrScriptChoice.LAV, OcrScriptChoice.bestForLocale(new Locale("lv")));
        assertEquals(OcrScriptChoice.RUS, OcrScriptChoice.bestForLocale(new Locale("ru")));
        assertEquals(OcrScriptChoice.UKR, OcrScriptChoice.bestForLocale(new Locale("uk")));
        assertEquals(OcrScriptChoice.SPA, OcrScriptChoice.bestForLocale(new Locale("es")));
        assertEquals(OcrScriptChoice.ENG, OcrScriptChoice.bestForLocale(Locale.ENGLISH));
        assertEquals(OcrScriptChoice.CHI_SIM, OcrScriptChoice.bestForLocale(Locale.SIMPLIFIED_CHINESE));
        assertEquals(OcrScriptChoice.CHI_TRA, OcrScriptChoice.bestForLocale(Locale.TRADITIONAL_CHINESE));
        assertEquals(OcrScriptChoice.NOR, OcrScriptChoice.bestForLocale(new Locale("nb")));
        assertEquals(OcrScriptChoice.SRP, OcrScriptChoice.bestForLocale(new Locale("sr")));
    }

    @Test
    public void searchProposesLanguagesByPrefix() {
        assertTrue(OcrScriptChoice.LIT.matchesQuery("l", "Lithuanian", "Lithuanian"));
        assertTrue(OcrScriptChoice.LAV.matchesQuery("l", "Latvian", "Latvian"));
        assertTrue(OcrScriptChoice.LIT.matchesQuery("li", "Літоўская", "Lithuanian"));
        assertTrue(OcrScriptChoice.BEL.matchesQuery("be", "Беларуская", "Belarusian"));
        assertTrue(OcrScriptChoice.KAZ.matchesQuery("ka", "Kazakh", "Kazakh"));
        assertTrue(OcrScriptChoice.KIR.matchesQuery("ky", "Kyrgyz", "Kyrgyz"));
        assertFalse(OcrScriptChoice.RUS.matchesQuery("l", "Russian", "Russian"));
    }
}
