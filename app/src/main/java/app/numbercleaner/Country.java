package app.numbercleaner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A calling-code entry. Lengths are the national significant number (NSN) length range,
 * i.e. the digits that follow the country code. They are used only to decide whether a
 * number typed without "+" already contains the country code.
 */
public final class Country {

    /** Special id for "don't add any country code". */
    public static final String NONE_ID = "NONE";
    /** Prefix for user-entered custom codes, e.g. "CUSTOM:1876". */
    public static final String CUSTOM_PREFIX = "CUSTOM:";

    public final String id;        // ISO 3166 alpha-2, NONE_ID, or CUSTOM:<digits>
    public final String name;
    public final String dialCode;  // digits only, "" for NONE
    public final int minLen;
    public final int maxLen;
    public final String trunk;     // national trunk prefix dropped when adding the code ("0", "8", "1" or "")
    public final String exit;      // international call prefix dialled from this country ("00", "011", ...)

    private Country(String id, String name, String dialCode, int minLen, int maxLen, String trunk, String exit) {
        this.id = id;
        this.name = name;
        this.dialCode = dialCode;
        this.minLen = minLen;
        this.maxLen = maxLen;
        this.trunk = trunk;
        this.exit = exit;
    }

    private static Country c(String iso, String name, String code, int min, int max, String trunk) {
        return new Country(iso, name, code, min, max, trunk, "00");
    }

    private static Country c(String iso, String name, String code, int min, int max, String trunk, String exit) {
        return new Country(iso, name, code, min, max, trunk, exit);
    }

    public static final Country NONE = new Country(NONE_ID, "No country code", "", 0, 0, "", "00");

    /** User-entered code: generic rules (drop a leading 0, NSN of 6–11 digits). */
    public static Country custom(String digits) {
        return new Country(CUSTOM_PREFIX + digits, "Custom", digits, 6, 11, "0", "00");
    }

    private static final List<Country> ALL;

    static {
        List<Country> l = new ArrayList<>(Arrays.asList(
                // Central & West Africa
                c("CM", "Cameroon", "237", 9, 9, ""),
                c("TD", "Chad", "235", 8, 8, ""),
                c("CF", "Central African Republic", "236", 8, 8, ""),
                c("GQ", "Equatorial Guinea", "240", 9, 9, ""),
                c("GA", "Gabon", "241", 7, 8, ""),
                c("CG", "Congo", "242", 9, 9, ""),
                c("CD", "Congo (DRC)", "243", 9, 9, "0"),
                c("NG", "Nigeria", "234", 8, 10, "0"),
                c("GH", "Ghana", "233", 9, 9, "0"),
                c("SN", "Senegal", "221", 9, 9, ""),
                c("CI", "Côte d'Ivoire", "225", 10, 10, ""),
                c("BJ", "Benin", "229", 8, 10, ""),
                c("TG", "Togo", "228", 8, 8, ""),
                c("ML", "Mali", "223", 8, 8, ""),
                c("BF", "Burkina Faso", "226", 8, 8, ""),
                c("NE", "Niger", "227", 8, 8, ""),
                c("GN", "Guinea", "224", 9, 9, ""),
                // East, North & Southern Africa
                c("KE", "Kenya", "254", 9, 9, "0"),
                c("UG", "Uganda", "256", 9, 9, "0"),
                c("TZ", "Tanzania", "255", 9, 9, "0"),
                c("RW", "Rwanda", "250", 9, 9, "0"),
                c("ET", "Ethiopia", "251", 9, 9, "0"),
                c("ZA", "South Africa", "27", 9, 9, "0"),
                c("EG", "Egypt", "20", 8, 10, "0"),
                c("MA", "Morocco", "212", 9, 9, "0"),
                c("DZ", "Algeria", "213", 8, 9, "0"),
                c("TN", "Tunisia", "216", 8, 8, ""),
                // Europe
                c("GB", "United Kingdom", "44", 9, 10, "0"),
                c("FR", "France", "33", 9, 9, "0"),
                c("DE", "Germany", "49", 6, 13, "0"),
                c("BE", "Belgium", "32", 8, 9, "0"),
                c("NL", "Netherlands", "31", 9, 9, "0"),
                c("CH", "Switzerland", "41", 9, 9, "0"),
                c("ES", "Spain", "34", 9, 9, ""),
                c("IT", "Italy", "39", 6, 11, ""),
                c("PT", "Portugal", "351", 9, 9, ""),
                c("IE", "Ireland", "353", 7, 9, "0"),
                c("RU", "Russia", "7", 10, 10, "8", "810"),
                c("TR", "Türkiye", "90", 10, 10, "0"),
                // Americas
                c("US", "United States", "1", 10, 10, "1", "011"),
                c("CA", "Canada", "1", 10, 10, "1", "011"),
                c("MX", "Mexico", "52", 10, 10, ""),
                c("BR", "Brazil", "55", 10, 11, "0"),
                // Middle East & Asia-Pacific
                c("AE", "United Arab Emirates", "971", 8, 9, "0"),
                c("SA", "Saudi Arabia", "966", 9, 9, "0"),
                c("IN", "India", "91", 10, 10, "0"),
                c("CN", "China", "86", 7, 11, "0"),
                c("JP", "Japan", "81", 9, 10, "0", "010"),
                c("AU", "Australia", "61", 9, 9, "0", "0011")
        ));
        Collections.sort(l, (a, b) -> a.name.compareToIgnoreCase(b.name));
        ALL = Collections.unmodifiableList(l);
    }

    /** All built-in countries, sorted by name. */
    public static List<Country> all() {
        return ALL;
    }

    public static Country byIso(String iso) {
        if (iso == null) return null;
        String up = iso.toUpperCase(Locale.ROOT);
        for (Country c : ALL) if (c.id.equals(up)) return c;
        return null;
    }

    /** Resolves a stored id (ISO, NONE or CUSTOM:digits). Returns null if unknown. */
    public static Country fromId(String id) {
        if (id == null) return null;
        if (NONE_ID.equals(id)) return NONE;
        if (id.startsWith(CUSTOM_PREFIX)) {
            String d = id.substring(CUSTOM_PREFIX.length());
            return isValidCode(d) ? custom(d) : null;
        }
        return byIso(id);
    }

    /** 1–4 digits, not starting with 0. */
    public static boolean isValidCode(String digits) {
        return digits != null && digits.matches("[1-9][0-9]{0,3}");
    }

    public boolean isNone() {
        return dialCode.isEmpty();
    }

    public boolean isCustom() {
        return id.startsWith(CUSTOM_PREFIX);
    }

    /** Regional-indicator flag emoji for ISO codes, empty otherwise. */
    public String flag() {
        if (id.length() != 2) return "";
        int first = Character.codePointAt(id, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(id, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    /** Label shown in the selector, e.g. "🇨🇲  Cameroon  +237". */
    public String label() {
        if (isNone()) return name;
        if (isCustom()) return "Custom  +" + dialCode;
        return flag() + "  " + name + "  +" + dialCode;
    }
}
