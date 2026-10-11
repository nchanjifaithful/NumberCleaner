package app.numbercleaner;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Turns whatever the user pasted ("(+237) 677-12-34-56", "0803 123 4567", "00 44 20 7946 0958")
 * into a parsed number: country + national significant number (NSN). {@link Result#format} then
 * writes it with or without "+" and the country code. Pure Java so it can be unit-tested off-device.
 */
public final class PhoneCleaner {

    public enum Kind {
        /** No digits in the input. */
        EMPTY,
        /** Input had a leading "+"; the country was read from its code. */
        INTERNATIONAL,
        /** Input began with an international call prefix (00, 011…), treated like "+". */
        EXIT_CONVERTED,
        /** Typed without "+", but already started with the chosen country's code (not added twice). */
        HAD_CODE,
        /** A local number of the chosen country. */
        LOCAL,
        /** Typed without "+", but recognised as another country's code + number. */
        DETECTED,
        /** "+" number whose country code isn't in the table: kept as typed. */
        UNKNOWN_CODE,
        /** Very short service/emergency code: left as typed. */
        SHORT_CODE
    }

    public static final class Result {
        public final Kind kind;
        /** Country of the number, or null for EMPTY / SHORT_CODE / UNKNOWN_CODE. */
        public final Country country;
        /** National significant number (digits after the country code); all digits when country is null. */
        public final String nsn;
        /** True when the country came from the number itself rather than the user's choice. */
        public final boolean detected;
        /** Correction that changed the digits (e.g. Cameroon's missing 6), or null. */
        public final String fix;
        /** Something that looks wrong (length, prefix). Never applied silently. */
        public final String warning;
        /** "Mobile" / "Fixed line" where the plan makes this clear (Cameroon), else null. */
        public final String lineType;
        /** Network the prefix was originally allocated to (Cameroon), else null. Ported numbers may differ. */
        public final String network;
        /** Trimmed input. */
        public final String original;

        Result(Kind kind, Country country, String nsn, boolean detected, String fix, String warning,
               String lineType, String original) {
            this(kind, country, nsn, detected, fix, warning, lineType, null, original);
        }

        Result(Kind kind, Country country, String nsn, boolean detected, String fix, String warning,
               String lineType, String network, String original) {
            this.network = network;
            this.kind = kind;
            this.country = country;
            this.nsn = nsn;
            this.detected = detected;
            this.fix = fix;
            this.warning = warning;
            this.lineType = lineType;
            this.original = original;
        }

        public boolean isEmpty() {
            return kind == Kind.EMPTY;
        }

        /** Canonical E.164 form ("+237677123456"); digits only for short codes. Used for storage. */
        public String e164() {
            return format(true, true);
        }

        /**
         * @param includeCode false → local format, e.g. "677123456" (Cameroon) or "08031234567" (Nigeria)
         * @param includePlus only used with the code: "+237…" vs "237…"
         */
        public String format(boolean includeCode, boolean includePlus) {
            switch (kind) {
                case EMPTY:
                    return "";
                case SHORT_CODE:
                    return nsn;
                case UNKNOWN_CODE:
                    // Without a known country the code can't be separated from the number: keep it.
                    return (includePlus ? "+" : "") + nsn;
                default:
                    if (includeCode) return (includePlus ? "+" : "") + country.dialCode + nsn;
                    return country.nationalPrefix() + nsn;
            }
        }

        /** True if the code can be removed for local format. */
        public boolean canDropCode() {
            return country != null;
        }

        @Override
        public String toString() {
            return e164() + " [" + kind + ", " + country + (fix != null ? ", fix: " + fix : "")
                    + (warning != null ? ", warn: " + warning : "") + "]";
        }
    }

    private static final int SHORT_CODE_MAX = 5;
    private static final int E164_MAX = 15;

    private static final Pattern EXTENSION = Pattern.compile(
            "(?i)\\s*(?:;\\s*ext\\s*=|(?<![a-z])(?:extension|ext\\.?|x)|#)\\s*\\d{1,6}\\s*$");
    private static final Pattern TRUNK_IN_BRACKETS = Pattern.compile("\\(\\s*0\\s*\\)");

    private PhoneCleaner() {
    }

    public static Result clean(String input, Country home) {
        return clean(input, home, null);
    }

    /**
     * @param home   the user's default country, used for local numbers
     * @param forced country the user explicitly picked for this number; disables guessing for
     *               numbers typed without "+" (a "+" or 00 number always keeps its own code)
     */
    public static Result clean(String input, Country home, Country forced) {
        if (input == null) input = "";
        String original = input.trim();
        // Full-width digits/plus (e.g. "＋２３７") → ASCII.
        String s = Normalizer.normalize(original, Normalizer.Form.NFKC);
        s = EXTENSION.matcher(s).replaceFirst("");

        boolean plus = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '+') plus = true;
            if (Character.isDigit(ch)) break;
        }
        if (plus) {
            // "+44 (0)20 7946 0958": the bracketed 0 is a trunk prefix that must not be dialled.
            s = TRUNK_IN_BRACKETS.matcher(s).replaceAll("");
        }

        String digits = digitsOf(s);
        if (digits.isEmpty()) return new Result(Kind.EMPTY, null, "", false, null, null, null, original);

        if (plus) return international(digits, home, Kind.INTERNATIONAL, original);

        Country ctx = forced != null ? forced : home;
        String exit = ctx != null && digits.startsWith(ctx.exit) ? ctx.exit
                : digits.startsWith("00") ? "00" : null;
        if (exit != null && digits.length() - exit.length() >= 7) {
            return international(digits.substring(exit.length()), home, Kind.EXIT_CONVERTED, original);
        }

        if (digits.length() <= SHORT_CODE_MAX) {
            return new Result(Kind.SHORT_CODE, null, digits, false, null, null, null, original);
        }
        if (ctx == null) return international(digits, null, Kind.DETECTED, original);

        String rest = digits.startsWith(ctx.dialCode) ? digits.substring(ctx.dialCode.length()) : null;
        boolean restOk = rest != null && !rest.isEmpty() && (ctx.fits(rest.length()) || isLegacy(ctx, rest));
        String local = localNsn(digits, ctx);
        boolean localOk = ctx.fits(local.length()) || isLegacy(ctx, local);

        // "237 677 12 34 56" typed without "+": the code is already there, don't duplicate it.
        if (restOk && !localOk) return build(ctx, rest, Kind.HAD_CODE, false, null, original);
        if (localOk || forced != null) return build(ctx, local, Kind.LOCAL, false, null, original);

        // Doesn't fit the home country: maybe another country's code without "+" ("447946095800").
        Country other = detectWithoutPlus(digits, ctx);
        if (other != null) {
            return build(other, digits.substring(other.dialCode.length()), Kind.DETECTED, true, null, original);
        }
        // Neither reading is valid. If it starts with the code and is longer than any local number,
        // assume the code was typed (and digits are missing) rather than adding the code twice.
        if (rest != null && !rest.isEmpty() && ctx.hasLengths() && local.length() > ctx.maxLen) {
            return build(ctx, rest, Kind.HAD_CODE, false, null, original);
        }
        return build(ctx, local, Kind.LOCAL, false, null, original);
    }

    // ---- Steps ----

    private static Result international(String digits, Country home, Kind kind, String original) {
        Country c = Country.detect(digits, home);
        if (c == null) {
            String warning = digits.length() > E164_MAX ? "Longer than 15 digits — double-check this number"
                    : "Country code not recognised — kept as typed";
            return new Result(Kind.UNKNOWN_CODE, null, digits, true, null, warning, null, original);
        }
        String nsn = digits.substring(c.dialCode.length());
        String fix = null;
        // "+234 0803 123 4567": the local 0 must not follow the country code.
        if (!c.trunk.isEmpty() && nsn.startsWith(c.trunk) && c.hasLengths()
                && !c.fits(nsn.length()) && c.fits(nsn.length() - c.trunk.length())) {
            nsn = nsn.substring(c.trunk.length());
            fix = "Removed the extra " + c.trunk + " after +" + c.dialCode;
        }
        return build(c, nsn, kind, true, fix, original);
    }

    /**
     * Country (≠ home) whose code + exact NSN length explains the digits. Conservative on purpose:
     * at least 11 digits in total and a country with ≥7-digit numbers, so a mistyped local number
     * (e.g. one digit too many) gets a length warning instead of becoming a random foreign number.
     */
    private static Country detectWithoutPlus(String digits, Country home) {
        if (digits.length() < 11) return null;
        for (int len = Math.min(4, digits.length() - 1); len >= 1; len--) {
            String code = digits.substring(0, len);
            int rest = digits.length() - len;
            Country best = null;
            for (Country c : Country.withCode(code)) {
                if (c == home || !c.hasLengths() || c.minLen < 7) continue;
                if (c.fits(rest) || isLegacy(c, digits.substring(len))) {
                    if (best == null) best = c;
                }
            }
            if (best != null) return "7".equals(code) ? Country.detect(digits, home) : best;
        }
        return null;
    }

    /** Drops the trunk prefix ("0803…" → "803…") when that gives a plausible national number. */
    private static String localNsn(String digits, Country c) {
        if (c.trunk.isEmpty() || !digits.startsWith(c.trunk) || digits.length() <= c.trunk.length()) {
            return digits;
        }
        String stripped = digits.substring(c.trunk.length());
        if (c.fits(stripped.length()) || isLegacy(c, stripped) || !c.fits(digits.length())) return stripped;
        return digits;
    }

    private static Result build(Country c, String nsn, Kind kind, boolean detected, String fix, String original) {
        String lineType = null;
        String warning = null;

        if (isLegacy(c, nsn)) {
            // Cameroon moved from 8 to 9 digits in 2014: mobiles gained a leading 6, fixed lines a 2.
            String add = nsn.charAt(0) == '2' || nsn.charAt(0) == '3' ? "2" : "6";
            nsn = add + nsn;
            fix = "Added the missing " + add + " (old 8-digit Cameroon number)";
        }

        if (nsn.isEmpty()) {
            warning = "Only the country code — the number is missing";
        } else if (c.hasLengths() && !c.fits(nsn.length())) {
            String expected = c.minLen == c.maxLen ? String.valueOf(c.minLen) : c.minLen + "–" + c.maxLen;
            warning = c.name + " numbers have " + expected + " digits after +" + c.dialCode
                    + " — this one has " + nsn.length();
        }
        String network = null;
        if (isCameroon(c) && nsn.length() == 9) {
            char first = nsn.charAt(0);
            if (first == '6') lineType = "Mobile";
            else if (first == '2') lineType = "Fixed line";
            else if (warning == null) warning = "Cameroon numbers start with 6 (mobile) or 2 (fixed line)";
            network = cameroonNetwork(nsn);
        }
        if (c.dialCode.length() + nsn.length() > E164_MAX) {
            warning = "Longer than 15 digits — double-check this number";
        }
        return new Result(kind, c, nsn, detected, fix, warning, lineType, network, original);
    }

    /**
     * Cameroon prefix → network it was allocated to (ART plan). Longest prefix wins.
     * Numbers can be ported, so the UI shows this as "likely". Update here if ART re-allocates ranges.
     */
    private static final String[][] CM_NETWORKS = {
            {"650", "MTN"}, {"651", "MTN"}, {"652", "MTN"}, {"653", "MTN"}, {"654", "MTN"},
            {"655", "Orange"}, {"656", "Orange"}, {"657", "Orange"}, {"658", "Orange"}, {"659", "Orange"},
            {"67", "MTN"}, {"68", "MTN"},
            {"69", "Orange"},
            {"66", "Nexttel"},
            {"62", "Camtel"},
            {"2", "Camtel"},
    };

    static String cameroonNetwork(String nsn) {
        String best = null;
        int bestLen = 0;
        for (String[] row : CM_NETWORKS) {
            if (nsn.startsWith(row[0]) && row[0].length() > bestLen) {
                best = row[1];
                bestLen = row[0].length();
            }
        }
        return best;
    }

    private static boolean isCameroon(Country c) {
        return "CM".equals(c.id);
    }

    /** Old 8-digit Cameroon numbers: mobiles began 5/7/9, fixed lines 2/3. */
    private static boolean isLegacy(Country c, String nsn) {
        return isCameroon(c) && nsn.length() == 8 && "23579".indexOf(nsn.charAt(0)) >= 0;
    }

    /** Extracts ASCII digits, accepting any Unicode decimal digit (e.g. Arabic-Indic). */
    static String digitsOf(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isDigit(cp)) sb.append((char) ('0' + Character.digit(cp, 10)));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
