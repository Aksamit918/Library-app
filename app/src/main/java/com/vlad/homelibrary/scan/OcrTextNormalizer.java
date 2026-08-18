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
        return cleaned.replaceAll("\\s+", " ").trim();
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
        if (looksLikeInitials(cleaned)) {
            return true;
        }
        int letters = countScript(cleaned, Character::isLetter);
        int digits = countScript(cleaned, Character::isDigit);
        return letters >= 2 || digits >= 2 || letters + digits >= 3;
    }

    static boolean isMostlyDigits(@Nullable String text) {
        String collapsed = collapseSpacedDigits(text);
        String digits = extractDigitRun(collapsed);
        if (digits.length() < 2) {
            return false;
        }
        int letters = countScript(collapsed, Character::isLetter);
        return digits.length() >= Math.max(2, letters);
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
