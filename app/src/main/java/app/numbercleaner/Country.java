package app.numbercleaner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A calling-code entry.
 * <ul>
 * <li>{@code minLen}/{@code maxLen}: national significant number (NSN) length — digits after the
 * country code. 0 means unknown (no length checks for that country).</li>
 * <li>{@code trunk}: national prefix dialled before the NSN inside the country ("0", "8", "1" or none).</li>
 * <li>{@code exit}: international call prefix dialled from the country ("00", "011", …).</li>
 * </ul>
 */
public final class Country {

    public final String id;        // ISO 3166 alpha-2
    public final String name;
    public final String dialCode;  // digits only
    public final int minLen;
    public final int maxLen;
    public final String trunk;
    public final String exit;

    private Country(String id, String name, String dialCode, String trunk, int minLen, int maxLen, String exit) {
        this.id = id;
        this.name = name;
        this.dialCode = dialCode;
        this.trunk = trunk;
        this.minLen = minLen;
        this.maxLen = maxLen;
        this.exit = exit;
    }

    /*
     * ISO|Name|code|trunk("-" = none)|min|max[|exit]
     * When several countries share a code, the first one listed is the default match.
     * Lengths follow the ITU-T E.164 national plans; Cameroon per ART (9 digits).
     */
    private static final String[] DATA = {
            // Africa
            "CM|Cameroon|237|-|9|9",
            "DZ|Algeria|213|0|8|9", "AO|Angola|244|-|9|9", "BJ|Benin|229|-|8|10", "BW|Botswana|267|-|7|8",
            "BF|Burkina Faso|226|-|8|8", "BI|Burundi|257|-|8|8", "CV|Cape Verde|238|-|7|7",
            "CF|Central African Republic|236|-|8|8", "TD|Chad|235|-|8|8", "KM|Comoros|269|-|7|7",
            "CG|Congo|242|-|9|9", "CD|Congo (DRC)|243|0|9|9", "CI|Côte d'Ivoire|225|-|10|10",
            "DJ|Djibouti|253|-|8|8", "EG|Egypt|20|0|8|10", "GQ|Equatorial Guinea|240|-|9|9",
            "ER|Eritrea|291|0|7|7", "SZ|Eswatini|268|-|8|8", "ET|Ethiopia|251|0|9|9", "GA|Gabon|241|-|7|8",
            "GM|Gambia|220|-|7|7", "GH|Ghana|233|0|9|9", "GN|Guinea|224|-|9|9", "GW|Guinea-Bissau|245|-|7|9",
            "KE|Kenya|254|0|9|9", "LS|Lesotho|266|-|8|8", "LR|Liberia|231|0|7|9", "LY|Libya|218|0|8|9",
            "MG|Madagascar|261|0|9|9", "MW|Malawi|265|0|7|9", "ML|Mali|223|-|8|8", "MR|Mauritania|222|-|8|8",
            "MU|Mauritius|230|-|7|8", "MA|Morocco|212|0|9|9", "MZ|Mozambique|258|-|8|9",
            "NA|Namibia|264|0|8|9", "NE|Niger|227|-|8|8", "NG|Nigeria|234|0|8|10", "RE|Réunion|262|0|9|9",
            "YT|Mayotte|262|0|9|9", "RW|Rwanda|250|0|9|9", "SH|Saint Helena|290|-|4|5",
            "ST|São Tomé and Príncipe|239|-|7|7", "SN|Senegal|221|-|9|9", "SC|Seychelles|248|-|7|7",
            "SL|Sierra Leone|232|0|8|8", "SO|Somalia|252|0|7|9", "ZA|South Africa|27|0|9|9",
            "SS|South Sudan|211|0|9|9", "SD|Sudan|249|0|9|9", "TZ|Tanzania|255|0|9|9", "TG|Togo|228|-|8|8",
            "TN|Tunisia|216|-|8|8", "UG|Uganda|256|0|9|9", "ZM|Zambia|260|0|9|9", "ZW|Zimbabwe|263|0|5|10",
            // Europe
            "GB|United Kingdom|44|0|9|10", "AL|Albania|355|0|8|9", "AD|Andorra|376|-|6|9",
            "AT|Austria|43|0|4|13", "BY|Belarus|375|8|9|9|810", "BE|Belgium|32|0|8|9",
            "BA|Bosnia and Herzegovina|387|0|8|9", "BG|Bulgaria|359|0|7|9", "HR|Croatia|385|0|8|9",
            "CY|Cyprus|357|-|8|8", "CZ|Czechia|420|-|9|9", "DK|Denmark|45|-|8|8", "EE|Estonia|372|-|7|8",
            "FO|Faroe Islands|298|-|6|6", "FI|Finland|358|0|5|12", "FR|France|33|0|9|9",
            "DE|Germany|49|0|6|13", "GI|Gibraltar|350|-|8|8", "GR|Greece|30|-|10|10",
            "GL|Greenland|299|-|6|6", "HU|Hungary|36|06|8|9", "IS|Iceland|354|-|7|7",
            "IE|Ireland|353|0|7|9", "IT|Italy|39|-|6|11", "XK|Kosovo|383|0|8|9", "LV|Latvia|371|-|8|8",
            "LI|Liechtenstein|423|-|7|9", "LT|Lithuania|370|8|8|8", "LU|Luxembourg|352|-|4|11",
            "MT|Malta|356|-|8|8", "MD|Moldova|373|0|8|8", "MC|Monaco|377|-|8|9", "ME|Montenegro|382|0|8|9",
            "NL|Netherlands|31|0|9|9", "MK|North Macedonia|389|0|8|8", "NO|Norway|47|-|8|8",
            "PL|Poland|48|-|9|9", "PT|Portugal|351|-|9|9", "RO|Romania|40|0|9|9",
            "RU|Russia|7|8|10|10|810", "KZ|Kazakhstan|7|8|10|10|810", "SM|San Marino|378|-|6|10",
            "RS|Serbia|381|0|8|9", "SK|Slovakia|421|0|9|9", "SI|Slovenia|386|0|8|8", "ES|Spain|34|-|9|9",
            "SE|Sweden|46|0|7|9", "CH|Switzerland|41|0|9|9", "TR|Türkiye|90|0|10|10", "UA|Ukraine|380|0|9|9",
            // Middle East & Asia
            "AF|Afghanistan|93|0|9|9", "AM|Armenia|374|0|8|8", "AZ|Azerbaijan|994|0|9|9",
            "BH|Bahrain|973|-|8|8", "BD|Bangladesh|880|0|10|10", "BT|Bhutan|975|-|7|8",
            "BN|Brunei|673|-|7|7", "KH|Cambodia|855|0|8|9", "CN|China|86|0|7|11", "GE|Georgia|995|0|9|9",
            "HK|Hong Kong|852|-|8|8", "IN|India|91|0|10|10", "ID|Indonesia|62|0|8|12", "IR|Iran|98|0|10|10",
            "IQ|Iraq|964|0|8|10", "IL|Israel|972|0|8|9", "JP|Japan|81|0|9|10|010", "JO|Jordan|962|0|8|9",
            "KW|Kuwait|965|-|8|8", "KG|Kyrgyzstan|996|0|9|9", "LA|Laos|856|0|8|10", "LB|Lebanon|961|0|7|8",
            "MO|Macau|853|-|8|8", "MY|Malaysia|60|0|8|10", "MV|Maldives|960|-|7|7", "MN|Mongolia|976|0|8|8",
            "MM|Myanmar|95|0|7|10", "NP|Nepal|977|0|8|10", "KP|North Korea|850|-|0|0", "OM|Oman|968|-|8|8",
            "PK|Pakistan|92|0|9|10", "PS|Palestine|970|0|8|9", "PH|Philippines|63|0|8|10",
            "QA|Qatar|974|-|7|8", "SA|Saudi Arabia|966|0|9|9", "SG|Singapore|65|-|8|8",
            "KR|South Korea|82|0|8|10", "LK|Sri Lanka|94|0|9|9", "SY|Syria|963|0|8|9", "TW|Taiwan|886|0|8|9",
            "TJ|Tajikistan|992|-|9|9", "TH|Thailand|66|0|8|9", "TL|Timor-Leste|670|-|7|8",
            "TM|Turkmenistan|993|8|8|8", "AE|United Arab Emirates|971|0|8|9", "UZ|Uzbekistan|998|-|9|9",
            "VN|Vietnam|84|0|9|10", "YE|Yemen|967|0|7|9",
            // Americas (+1 countries other than US/CA are listed with their area code)
            "US|United States|1|1|10|10|011", "CA|Canada|1|1|10|10|011",
            "AG|Antigua and Barbuda|1268|-|7|7|011", "BS|Bahamas|1242|-|7|7|011",
            "BB|Barbados|1246|-|7|7|011", "BM|Bermuda|1441|-|7|7|011", "KY|Cayman Islands|1345|-|7|7|011",
            "DM|Dominica|1767|-|7|7|011", "DO|Dominican Republic|1809|-|7|7|011",
            "GD|Grenada|1473|-|7|7|011", "JM|Jamaica|1876|-|7|7|011", "PR|Puerto Rico|1787|-|7|7|011",
            "KN|Saint Kitts and Nevis|1869|-|7|7|011", "LC|Saint Lucia|1758|-|7|7|011",
            "VC|Saint Vincent and the Grenadines|1784|-|7|7|011", "TT|Trinidad and Tobago|1868|-|7|7|011",
            "TC|Turks and Caicos|1649|-|7|7|011", "VG|British Virgin Islands|1284|-|7|7|011",
            "VI|US Virgin Islands|1340|-|7|7|011", "GU|Guam|1671|-|7|7|011",
            "MX|Mexico|52|-|10|10", "BR|Brazil|55|0|10|11", "AR|Argentina|54|0|10|11", "CL|Chile|56|-|9|9",
            "CO|Colombia|57|-|8|10", "PE|Peru|51|0|8|9", "VE|Venezuela|58|0|10|10", "EC|Ecuador|593|0|8|9",
            "BO|Bolivia|591|0|8|8", "PY|Paraguay|595|0|9|9", "UY|Uruguay|598|0|8|8", "GY|Guyana|592|-|7|7",
            "SR|Suriname|597|-|6|7", "GF|French Guiana|594|0|9|9", "CR|Costa Rica|506|-|8|8",
            "PA|Panama|507|-|7|8", "GT|Guatemala|502|-|8|8", "HN|Honduras|504|-|8|8",
            "SV|El Salvador|503|-|8|8", "NI|Nicaragua|505|-|8|8", "BZ|Belize|501|-|7|7", "CU|Cuba|53|0|8|8",
            "HT|Haiti|509|-|8|8", "GP|Guadeloupe|590|0|9|9", "MQ|Martinique|596|0|9|9",
            // Oceania
            "AU|Australia|61|0|9|9|0011", "NZ|New Zealand|64|0|8|10", "FJ|Fiji|679|-|7|7",
            "PG|Papua New Guinea|675|-|7|8", "SB|Solomon Islands|677|-|5|7", "VU|Vanuatu|678|-|5|7",
            "NC|New Caledonia|687|-|6|6", "PF|French Polynesia|689|-|8|8", "WS|Samoa|685|-|5|7",
            "TO|Tonga|676|-|5|7", "KI|Kiribati|686|-|5|8", "FM|Micronesia|691|-|7|7",
            "MH|Marshall Islands|692|-|7|7", "PW|Palau|680|-|7|7", "NR|Nauru|674|-|7|7",
            "TV|Tuvalu|688|-|5|6", "CK|Cook Islands|682|-|5|5",
    };

    private static final List<Country> SORTED;
    private static final Map<String, Country> BY_ISO = new HashMap<>();
    private static final Map<String, List<Country>> BY_CODE = new HashMap<>();

    static {
        List<Country> list = new ArrayList<>();
        for (String row : DATA) {
            String[] f = row.split("\\|");
            String trunk = "-".equals(f[3]) ? "" : f[3];
            String exit = f.length > 6 ? f[6] : "00";
            Country c = new Country(f[0], f[1], f[2], trunk, Integer.parseInt(f[4]), Integer.parseInt(f[5]), exit);
            list.add(c);
            BY_ISO.put(c.id, c);
            List<Country> same = BY_CODE.get(c.dialCode);
            if (same == null) BY_CODE.put(c.dialCode, same = new ArrayList<>());
            same.add(c);
        }
        Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
        SORTED = Collections.unmodifiableList(list);
    }

    /** All countries, sorted by name. */
    public static List<Country> all() {
        return SORTED;
    }

    public static Country byIso(String iso) {
        return iso == null ? null : BY_ISO.get(iso.toUpperCase(Locale.ROOT));
    }

    /** Resolves a stored id. Returns null for unknown ids (including old "NONE"/"CUSTOM:" values). */
    public static Country fromId(String id) {
        return byIso(id);
    }

    /** All countries whose calling code is exactly {@code code}. */
    static List<Country> withCode(String code) {
        List<Country> l = BY_CODE.get(code);
        return l == null ? Collections.<Country>emptyList() : l;
    }

    /**
     * Finds the country whose calling code starts {@code digits} (digits after "+").
     * Longest code wins (+1876 Jamaica before +1). Where a code is shared, the user's home country
     * is preferred, then a few well-known splits (+7 6/7… = Kazakhstan), then the first listed.
     */
    public static Country detect(String digits, Country home) {
        for (int len = Math.min(4, digits.length()); len >= 1; len--) {
            List<Country> list = BY_CODE.get(digits.substring(0, len));
            if (list == null) continue;
            if (home != null && list.contains(home)) return home;
            if ("7".equals(list.get(0).dialCode) && digits.length() > 1
                    && (digits.charAt(1) == '6' || digits.charAt(1) == '7')) {
                return byIso("KZ");
            }
            return list.get(0);
        }
        return null;
    }

    public boolean hasLengths() {
        return minLen > 0;
    }

    /** Whether an NSN of this length is valid. Unknown-length countries accept 4–14. */
    public boolean fits(int len) {
        return hasLengths() ? len >= minLen && len <= maxLen : len >= 4 && len <= 14;
    }

    /** Prefix used when writing the number in local format (no country code). */
    public String nationalPrefix() {
        return "1".equals(trunk) ? "" : trunk;   // US/Canada local format is the plain 10 digits
    }

    /** Regional-indicator flag emoji. */
    public String flag() {
        if (id.length() != 2) return "";
        int first = Character.codePointAt(id, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(id, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    /** "🇨🇲 Cameroon +237" */
    public String label() {
        return flag() + "  " + name + "  +" + dialCode;
    }

    @Override
    public String toString() {
        return id + "(+" + dialCode + ")";
    }
}
