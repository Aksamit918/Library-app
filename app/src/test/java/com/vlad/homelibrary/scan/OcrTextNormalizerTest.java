package com.vlad.homelibrary.scan;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OcrTextNormalizerTest {

    @Test
    public void keepsNumbersAndInitials() {
        assertTrue(OcrTextNormalizer.isKeepableLine("5334"));
        assertTrue(OcrTextNormalizer.isKeepableLine("42"));
        assertTrue(OcrTextNormalizer.isKeepableLine("A. I."));
        assertTrue(OcrTextNormalizer.isKeepableLine("А. И."));
        assertTrue(OcrTextNormalizer.isKeepableLine("X.X."));
        assertFalse(OcrTextNormalizer.isKeepableLine("A"));
        assertFalse(OcrTextNormalizer.isKeepableLine("8"));
    }

    @Test
    public void normalizesAuthorInitials() {
        assertEquals("A. I. Mister", OcrTextNormalizer.normalizeAuthorInitials("A.I.Mister"));
        assertEquals("A. I. Mister", OcrTextNormalizer.normalizeAuthorInitials("A I Mister"));
        assertEquals("A. I. Mister", OcrTextNormalizer.normalizeAuthorInitials("A.I. Mister"));
        assertEquals("А. И. Курак, А. А. Подупейко",
                OcrTextNormalizer.normalizeAuthorInitials("А И Курак, А А Подупейко"));
        assertEquals("А. И. Курак", OcrTextNormalizer.normalizeAuthorInitials("А. И. Курак"));
    }

    @Test
    public void doesNotMergeShortInitialsIntoLongerName() {
        List<String> lines = new ArrayList<>(Arrays.asList("А. И. Курак"));
        assertEquals(-1, OcrTextNormalizer.indexOfSimilar(lines, "А"));
        assertEquals(-1, OcrTextNormalizer.indexOfSimilar(lines, "А."));
    }

    @Test
    public void doesNotMergeDigitsWithLetters() {
        List<String> lines = new ArrayList<>(Arrays.asList("ШАХМАТЫ"));
        assertEquals(-1, OcrTextNormalizer.indexOfSimilar(lines, "5334"));
    }

    @Test
    public void extractsLongestDigitRun() {
        assertEquals("5334", OcrTextNormalizer.extractDigitRun("ШАХМАТЫ 5334 cl"));
        assertEquals("9994", OcrTextNormalizer.extractDigitRun("ВЕЦ, 9994"));
        assertEquals("5334", OcrTextNormalizer.extractDigitRun("5 3 3 4"));
        assertEquals("5334", OcrTextNormalizer.extractDigitRun("5 3 3 А"));
        assertEquals("5334", OcrTextNormalizer.extractDigitRun("5 3 3 А КОМБИНАЦИИ"));
        assertEquals("", OcrTextNormalizer.extractDigitRun("ШАХМАТЫ"));
    }

    @Test
    public void collapsesSpacedDigitsAndSplitsWords() {
        assertEquals("5334 КОМБИНАЦИИ",
                OcrTextNormalizer.collapseSpacedDigits("5 3 3 А КОМБИНАЦИИ"));
        assertEquals("5334", OcrTextNormalizer.collapseSpacedDigits("5 3 3 4"));
        assertEquals(
                Arrays.asList("5334", "КОМБИНАЦИИ"),
                OcrTextNormalizer.explodeMixedNumberLine("5 3 3 А КОМБИНАЦИИ"));
        assertEquals(
                Arrays.asList("ЗАДАЧИ"),
                OcrTextNormalizer.explodeMixedNumberLine("ЗАДАЧИ"));
        assertEquals("А. И. Курак",
                OcrTextNormalizer.collapseSpacedDigits("А. И. Курак"));
    }
}
