package com.vlad.homelibrary.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

final class OcrTextNormalizer {

    private static final Pattern TRIPLE_INITIALS = Pattern.compile(
            "(?u)(?<!\\p{L})(\\p{L})\\s*\\.\\s*(\\p{L})\\s*\\.\\s*(\\p{L})\\s*\\.\\s*(?=\\p{L}{2,})");
    private static final Pattern DOUBLE_INITIALS = Pattern.compile(
            "(?u)(?<!\\p{L})(\\p{L})\\s*\\.\\s*(\\p{L})\\s*\\.\\s*(?=\\p{L}{2,})");
    private static final Pattern DOUBLE_INITIALS_NO_DOTS = Pattern.compile(
            "(?u)(?<!\\p{L})(\\p{L})(?!\\.)\\s+(\\p{L})(?!\\.)\\s+(?=\\p{L}{2,})");
    private static final Pattern DOUBLE_INITIALS_ONE_DOT = Pattern.compile(
            "(?u)(?<!\\p{L})(\\p{L})\\.(\\p{L})(?!\\.)\\s+(?=\\p{L}{2,})");
    private static final Pattern INITIALS_ONLY = Pattern.compile(
            "(?u)^(\\p{L}\\.\\s*){1,4}\\p{L}?\\.?$");
    private static final Pattern LEADING_JUNK = Pattern.compile("^[|_=\\-•·«»\"'“”]+");
    private static final Pattern TRAILING_JUNK = Pattern.compile("[|_=\\-•·«»\"'“”]+$");
    private static final Pattern MATH_TOKEN = Pattern.compile(
            "(?i)(sgn|chi|sin|cos|tan|log|exp)\\b|[χλφψ]|[_^=]");

    private OcrTextNormalizer() {
    }

    static int digitLookalike(int cp) {
        if (Character.isDigit(cp)) {
            return cp;
        }
        switch (cp) {
            case 'O':
            case 'o':
            case 'О':
            case 'о':
            case 'Q':
            case 'D':
                return '0';
            case 'I':
            case 'l':
            case '|':
            case 'І':
            case 'і':
                return '1';
            case 'Z':
            case 'z':
            case 'З':
            case 'з':
                return '3';
            case 'A':
            case 'a':
            case 'А':
            case 'а':
                return '4';
            case 'S':
            case 's':
                return '5';
            case 'G':
            case 'b':
                return '6';
            case 'T':
                return '7';
            case 'B':
            case 'В':
                return '8';
            case 'g':
            case 'q':
                return '9';
            default:
                return -1;
        }
    }

    @NonNull
    static String cleanFragment(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().replace('\u00A0', ' ');
        cleaned = LEADING_JUNK.matcher(cleaned).replaceAll("");
        cleaned = TRAILING_JUNK.matcher(cleaned).replaceAll("");
        cleaned = stripUnbalancedEdges(cleaned);
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    @NonNull
    static String stripUnbalancedEdges(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String cleaned = value.trim();
        while (cleaned.length() >= 2) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if ((last == ')' && cleaned.indexOf('(') < 0)
                    || (last == ']' && cleaned.indexOf('[') < 0)
                    || (last == '}' && cleaned.indexOf('{') < 0)) {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
                continue;
            }
            char first = cleaned.charAt(0);
            if ((first == '(' && cleaned.indexOf(')') < 0)
                    || (first == '[' && cleaned.indexOf(']') < 0)
                    || (first == '{' && cleaned.indexOf('}') < 0)) {
                cleaned = cleaned.substring(1).trim();
                continue;
            }
            break;
        }
        return cleaned;
    }

    @NonNull
    static String cleanOcrLine(@Nullable String text) {
        return normalizeAuthorInitials(text);
    }

    @NonNull
    static String normalizeAuthorInitials(@Nullable String text) {
        String cleaned = cleanFragment(text);
        if (cleaned.isEmpty()) {
            return "";
        }
        String result = TRIPLE_INITIALS.matcher(cleaned).replaceAll("$1. $2. $3. ");
        result = DOUBLE_INITIALS.matcher(result).replaceAll("$1. $2. ");
        result = DOUBLE_INITIALS_NO_DOTS.matcher(result).replaceAll("$1. $2. ");
        result = DOUBLE_INITIALS_ONE_DOT.matcher(result).replaceAll("$1. $2. ");
        return result.replaceAll("\\s+", " ").trim();
    }

    static boolean looksLikeInitials(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return INITIALS_ONLY.matcher(text.trim()).matches();
    }

    static boolean isKeepableLine(@Nullable String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) {
            return false;
        }
        if (looksLikeMathNoise(cleaned) || isMixedScriptNoise(cleaned)) {
            return false;
        }
        if (looksLikeInitials(cleaned)) {
            return true;
        }
        int letters = countScript(cleaned, Character::isLetter);
        int digits = countScript(cleaned, Character::isDigit);
        if (digits >= 4 && letters <= 1) {
            return true;
        }
        return hasUsefulWord(cleaned);
    }

    static boolean hasUsefulWord(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int wordsGe5 = 0;
        int wordsGe4 = 0;
        int words = 0;
        int currentLetters = 0;
        for (int i = 0; i <= text.length(); ) {
            boolean end = i >= text.length();
            int cp = end ? ' ' : text.codePointAt(i);
            if (!end && Character.isLetter(cp)) {
                currentLetters++;
                i += Character.charCount(cp);
                continue;
            }
            if (currentLetters > 0) {
                words++;
                if (currentLetters >= 5) {
                    wordsGe5++;
                }
                if (currentLetters >= 4) {
                    wordsGe4++;
                }
                currentLetters = 0;
            }
            if (end) {
                break;
            }
            i += Character.charCount(cp);
        }
        return wordsGe5 >= 1 || wordsGe4 >= 2 || (words == 1 && wordsGe4 == 1);
    }

    static boolean isMixedScriptNoise(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int cyrillic = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetter(cp)) {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
                if (block == Character.UnicodeBlock.CYRILLIC
                        || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY) {
                    cyrillic++;
                } else if (block == Character.UnicodeBlock.BASIC_LATIN
                        || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                        || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                        || block == Character.UnicodeBlock.LATIN_EXTENDED_B) {
                    latin++;
                }
            }
            i += Character.charCount(cp);
        }
        return cyrillic >= 2 && latin >= 2;
    }

    static boolean looksLikeGibberish(@Nullable String text) {
        if (text == null || text.isEmpty() || looksLikeInitials(text)) {
            return false;
        }
        int letters = countScript(text, Character::isLetter);
        int digits = countScript(text, Character::isDigit);
        if (digits >= 4 && letters <= 1) {
            return false;
        }
        return !hasUsefulWord(text);
    }

    static boolean looksLikeMathNoise(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (MATH_TOKEN.matcher(text).find() && countScript(text, Character::isLetter) < 8) {
            return true;
        }
        int letters = countScript(text, Character::isLetter);
        int digits = countScript(text, Character::isDigit);
        int ops = 0;
        int parens = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == '(' || cp == ')' || cp == '[' || cp == ']' || cp == '{' || cp == '}') {
                parens++;
            } else if (cp == '=' || cp == '+' || cp == '*' || cp == '/' || cp == '^'
                    || cp == '_' || cp == '<' || cp == '>' || cp == '|' || cp == '\\'
                    || cp == '×' || cp == '−') {
                ops++;
            }
            i += Character.charCount(cp);
        }
        int alnum = letters + digits;
        if (parens >= 2 && alnum < 6) {
            return true;
        }
        return ops + parens >= 3 && alnum <= ops + parens + 3;
    }

    static boolean mismatchesChosenScript(@Nullable String text, @NonNull OcrScriptChoice.Family family) {
        if (text == null || text.isEmpty() || isMostlyDigits(text) || looksLikeInitials(text)) {
            return false;
        }
        int cyrillic = 0;
        int latin = 0;
        int otherLetters = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetter(cp)) {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
                if (block == Character.UnicodeBlock.CYRILLIC
                        || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY) {
                    cyrillic++;
                } else if (block == Character.UnicodeBlock.BASIC_LATIN
                        || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                        || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                        || block == Character.UnicodeBlock.LATIN_EXTENDED_B) {
                    latin++;
                } else {
                    otherLetters++;
                }
            }
            i += Character.charCount(cp);
        }
        if (family == OcrScriptChoice.Family.CYRILLIC) {
            return cyrillic == 0 && latin >= 2 && otherLetters == 0;
        }
        if (family == OcrScriptChoice.Family.LATIN) {
            return latin == 0 && cyrillic >= 2 && otherLetters == 0;
        }
        return false;
    }

    static boolean isMostlyDigits(@Nullable String text) {
        String collapsed = collapseSpacedDigits(text);
        String digits = extractDigitRun(collapsed);
        if (digits.length() < 4) {
            return false;
        }
        int letters = countScript(collapsed, Character::isLetter);
        return letters <= 1;
    }

    @NonNull
    static String collapseSpacedDigits(@Nullable String text) {
        String cleaned = cleanFragment(text);
        if (cleaned.isEmpty()) {
            return "";
        }
        String[] tokens = cleaned.split("\\s+");
        StringBuilder out = new StringBuilder();
        StringBuilder number = new StringBuilder();
        int realDigits = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            boolean digitToken = isDigitToken(token);
            boolean lookalike = isSingleLookalike(token);
            boolean nextDigit = i + 1 < tokens.length && isDigitToken(tokens[i + 1]);
            if (digitToken || (lookalike && (realDigits > 0 || nextDigit))) {
                appendDigitToken(number, token);
                realDigits += countScript(token, Character::isDigit);
                continue;
            }
            flushNumber(out, number, realDigits);
            realDigits = 0;
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(token);
        }
        flushNumber(out, number, realDigits);
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    @NonNull
    static List<String> explodeMixedNumberLine(@Nullable String text) {
        List<String> parts = new ArrayList<>();
        String collapsed = collapseSpacedDigits(text);
        if (collapsed.isEmpty()) {
            return parts;
        }
        String digits = extractDigitRun(collapsed);
        if (digits.length() < 2) {
            parts.add(collapsed);
            return parts;
        }
        int index = collapsed.indexOf(digits);
        if (index < 0) {
            parts.add(collapsed);
            return parts;
        }
        String before = cleanFragment(collapsed.substring(0, index));
        String after = cleanFragment(collapsed.substring(index + digits.length()));
        if (countScript(before, Character::isLetter) >= 2) {
            parts.add(before);
        }
        parts.add(digits);
        if (countScript(after, Character::isLetter) >= 2) {
            parts.add(after);
        }
        if (parts.isEmpty()) {
            parts.add(collapsed);
        }
        return parts;
    }

    @NonNull
    static String extractDigitRun(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder compact = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                compact.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        String best = "";
        StringBuilder current = new StringBuilder();
        int realDigits = 0;
        for (int i = 0; i < compact.length(); ) {
            int cp = compact.codePointAt(i);
            int mapped = digitLookalike(cp);
            boolean real = Character.isDigit(cp);
            if (real || mapped >= 0) {
                current.append((char) (real ? cp : mapped));
                if (real) {
                    realDigits++;
                }
            } else {
                if (realDigits >= 2 && current.length() > best.length()) {
                    best = current.toString();
                }
                current = new StringBuilder();
                realDigits = 0;
            }
            i += Character.charCount(cp);
        }
        if (realDigits >= 2 && current.length() > best.length()) {
            best = current.toString();
        }
        return best;
    }

    static int indexOfSimilar(List<String> lines, String candidate) {
        String key = lettersKey(candidate);
        if (key.length() < 2) {
            return -1;
        }
        boolean candidateDigits = isDigitKey(key);
        for (int i = 0; i < lines.size(); i++) {
            String other = lettersKey(lines.get(i));
            if (other.length() < 2) {
                continue;
            }
            if (other.equals(key)) {
                return i;
            }
            boolean otherDigits = isDigitKey(other);
            if (!candidateDigits && !otherDigits && isNoisyVariant(key, other)) {
                return i;
            }
            if (candidateDigits != otherDigits) {
                continue;
            }
            String shorter = key.length() <= other.length() ? key : other;
            String longer = key.length() <= other.length() ? other : key;
            if (shorter.length() < 3) {
                continue;
            }
            if (longer.contains(shorter)
                    && shorter.length() * 100 >= longer.length() * 55) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    static String lettersKey(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
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

    static int countAlnum(@Nullable String text) {
        return countScript(text, cp -> Character.isLetter(cp) || Character.isDigit(cp));
    }

    static int countScript(@Nullable String text, IntPredicate matcher) {
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

    static boolean isNoisyVariant(@NonNull String a, @NonNull String b) {
        if (a.equals(b)) {
            return true;
        }
        int min = Math.min(a.length(), b.length());
        int max = Math.max(a.length(), b.length());
        if (min < 4 || max < 4) {
            return false;
        }
        if (min * 100 < max * 70) {
            return false;
        }
        int dist = editDistance(a, b);
        return dist <= 1 || dist * 6 <= max;
    }

    static int editDistance(@NonNull String a, @NonNull String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[m];
    }

    static int lineScore(@Nullable String text, float confidence) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int letters = countScript(text, Character::isLetter);
        int digits = countScript(text, Character::isDigit);
        int score = letters * 4 + digits * 3 + Math.round(confidence);
        if (looksLikeMathNoise(text)) {
            score -= 80;
        }
        return score;
    }

    private static boolean isDigitKey(@NonNull String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigitToken(@NonNull String token) {
        int digits = 0;
        int other = 0;
        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            if (Character.isDigit(cp) || digitLookalike(cp) >= 0) {
                if (Character.isDigit(cp)) {
                    digits++;
                }
            } else if (!isIgnorablePunct(cp)) {
                other++;
            }
            i += Character.charCount(cp);
        }
        return digits >= 1 && other == 0;
    }

    private static boolean isSingleLookalike(@NonNull String token) {
        if (token.length() != 1) {
            return false;
        }
        int cp = token.codePointAt(0);
        return !Character.isDigit(cp) && digitLookalike(cp) >= 0;
    }

    private static void appendDigitToken(@NonNull StringBuilder number, @NonNull String token) {
        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            int mapped = digitLookalike(cp);
            if (Character.isDigit(cp)) {
                number.appendCodePoint(cp);
            } else if (mapped >= 0) {
                number.append((char) mapped);
            }
            i += Character.charCount(cp);
        }
    }

    private static void flushNumber(@NonNull StringBuilder out,
                                    @NonNull StringBuilder number,
                                    int realDigits) {
        if (number.length() == 0) {
            return;
        }
        if (realDigits >= 2) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(number);
        }
        number.setLength(0);
    }

    private static boolean isIgnorablePunct(int cp) {
        return cp == '.' || cp == ',' || cp == ':' || cp == '-';
    }
}
