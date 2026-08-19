package com.vlad.homelibrary.scan;

public final class ScanResultParser {

    public static final String EXTRA_SCANNED_VALUE = "scanned_value";
    public static final String EXTRA_SCANNED_TYPE = "scanned_type";
    @Deprecated
    public static final String EXTRA_SCANNED_ISBN = "scanned_isbn";

    public enum ScanType {
        ISBN,
        ASIN,
        OTHER,
        TITLE_TEXT
    }

    private ScanResultParser() {
    }

    public static ScanType parseBarcode(String rawValue) {
        String normalized = normalize(rawValue);
        if (normalized.isEmpty()) {
            return ScanType.OTHER;
        }
        if (isIsbn13(normalized) || isIsbn10(normalized)) {
            return ScanType.ISBN;
        }
        if (isAsin(normalized)) {
            return ScanType.ASIN;
        }
        return ScanType.OTHER;
    }

    public static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.replaceAll("[\\s-]", "").toUpperCase();
    }

    private static boolean isIsbn13(String value) {
        if (!value.matches("\\d{13}")) {
            return false;
        }
        if (!(value.startsWith("978") || value.startsWith("979"))) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = value.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == (value.charAt(12) - '0');
    }

    private static boolean isIsbn10(String value) {
        if (!value.matches("\\d{9}[\\dX]")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (value.charAt(i) - '0') * (10 - i);
        }
        char last = value.charAt(9);
        sum += (last == 'X') ? 10 : (last - '0');
        return sum % 11 == 0;
    }

    private static boolean isAsin(String value) {
        return value.matches("[A-Z0-9]{10}") && !isIsbn10(value);
    }
}
