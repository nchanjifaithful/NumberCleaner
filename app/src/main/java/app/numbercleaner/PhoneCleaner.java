package app.numbercleaner;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Turns whatever the user pasted ("(+237) 677-12-34-56", "tel:0803 123 4567", "00 44 20 7946 0958")
 * into a compact, dialable number ("+237677123456"). Pure Java so it can be unit-tested off-device.
 */
public final class PhoneCleaner {

    public enum Kind {
        /** No digits in the input. */
        EMPTY,
        /** Input already had a leading "+"; only formatting characters were removed. */
        INTERNATIONAL,
        /** Input began with an international call prefix (00, 011…), converted to "+". */
        EXIT_CONVERTED,
        /** The selected country code was already present (without "+"); "+" was restored. */
        HAD_CODE,
        /** The selected country code was added to a local number. */
        ADDED_CODE,
        /** No country code selected: digits only. */
        DIGITS_ONLY,
        /** Very short service/emergency code: left as typed digits. */
        SHORT_CODE
    }

    public static final class Result {
        public final String number;
        public final Kind kind;
        /** True when the cleaned number is exactly what the user typed (nothing needed changing). */
        public final boolean unchanged;
        /** Optional human-readable warning, or null. */
        public final String warning;

        Result(String number, Kind kind, boolean unchanged, String warning) {
            this.number = number;
            this.kind = kind;
            this.unchanged = unchanged;
            this.warning = warning;
        }

        public boolean isEmpty() {
            return kind == Kind.EMPTY;
        }

        @Override
        public String toString() {
            return number + " [" + kind + (warning != null ? ", " + warning : "") + "]";
        }
    }

    private static final int SHORT_CODE_MAX = 5;
    private static final int E164_MAX = 15;
    private static final int INTL_MIN = 8; // "+" plus at least 8 digits is the shortest realistic full number

    private static final Pattern EXTENSION = Pattern.compile(
            "(?i)\\s*(?:;\\s*ext\\s*=|(?<![a-z])(?:extension|ext\\.?|x)|#)\\s*\\d{1,6}\\s*$");
    private static final Pattern TRUNK_IN_BRACKETS = Pattern.compile("\\(\\s*0\\s*\\)");

    private PhoneCleaner() {
    }

    public static Result clean(String input, Country country) {
        if (input == null) return empty();
        if (country == null) country = Country.NONE;

        String original = input.trim();
        // Full-width digits/plus (e.g. "＋２３７") → ASCII.
        String s = Normalizer.normalize(original, Normalizer.Form.NFKC);
        s = EXTENSION.matcher(s).replaceFirst("");

        boolean plus = false;
        boolean seenDigit = false;
        for (int i = 0; i < s.length() && !seenDigit; i++) {
            char ch = s.charAt(i);
            if (ch == '+') plus = true;
            else if (Character.isDigit(ch)) seenDigit = true;
        }
        if (plus) {
            // "+44 (0)20 7946 0958": the bracketed 0 is a trunk prefix that must not be dialled.
            s = TRUNK_IN_BRACKETS.matcher(s).replaceAll("");
        }

        String digits = digitsOf(s);
        if (digits.isEmpty()) return empty();

        if (plus) {
            String out = "+" + digits;
            return result(out, Kind.INTERNATIONAL, original, intlWarning(digits));
        }

        // International call prefix typed instead of "+" (00 237…, 011 44…).
        String exit = digits.startsWith(country.exit) ? country.exit : (digits.startsWith("00") ? "00" : null);
        if (exit != null && digits.length() - exit.length() >= 7) {
            String rest = digits.substring(exit.length());
            return result("+" + rest, Kind.EXIT_CONVERTED, original, intlWarning(rest));
        }

        if (digits.length() <= SHORT_CODE_MAX) {
            return result(digits, Kind.SHORT_CODE, original, null);
        }

        if (country.isNone()) {
            return result(digits, Kind.DIGITS_ONLY, original, null);
        }

        String cc = country.dialCode;
        int len = digits.length();
        boolean fitsNational = len >= country.minLen && len <= country.maxLen;
        int rem = len - cc.length();
        boolean fitsWithCode = digits.startsWith(cc) && rem >= country.minLen && rem <= country.maxLen;

        // "237 677 12 34 56" typed without "+": the code is already there, don't duplicate it.
        // Only when the digits wouldn't also be a valid national number on their own.
        if (fitsWithCode && !fitsNational) {
            return result("+" + digits, Kind.HAD_CODE, original, null);
        }

        String national = digits;
        if (!country.trunk.isEmpty() && digits.startsWith(country.trunk)
                && digits.length() > country.trunk.length()) {
            String stripped = digits.substring(country.trunk.length());
            // Strip the trunk prefix when the rest is a plausible national number,
            // or when the number is too long to be national with the prefix kept.
            if (stripped.length() >= country.minLen || !fitsNational) national = stripped;
        }

        String warning = null;
        if (national.length() < country.minLen || national.length() > country.maxLen) {
            warning = lengthWarning(country, national.length());
        }
        if (cc.length() + national.length() > E164_MAX) {
            warning = "Longer than 15 digits — double-check this number";
        }
        return result("+" + cc + national, Kind.ADDED_CODE, original, warning);
    }

    /** Extracts ASCII digits, accepting any Unicode decimal digit (e.g. Arabic-Indic). */
    static String digitsOf(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int d = Character.digit(cp, 10);
            if (d >= 0 && Character.isDigit(cp)) sb.append((char) ('0' + d));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static String intlWarning(String digits) {
        if (digits.length() > E164_MAX) return "Longer than 15 digits — double-check this number";
        if (digits.length() < INTL_MIN) return "Looks too short for an international number";
        return null;
    }

    private static String lengthWarning(Country c, int actual) {
        if (c.isCustom()) return null;
        String expected = c.minLen == c.maxLen ? String.valueOf(c.minLen) : c.minLen + "–" + c.maxLen;
        return c.name + " numbers have " + expected + " digits after +" + c.dialCode + " (this has " + actual + ")";
    }

    private static Result result(String out, Kind kind, String original, String warning) {
        return new Result(out, kind, out.equals(original), warning);
    }

    private static Result empty() {
        return new Result("", Kind.EMPTY, false, null);
    }
}
