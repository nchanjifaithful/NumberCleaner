package app.numbercleaner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneCleanerTest {

    private static final Country CM = Country.byIso("CM");
    private static final Country NG = Country.byIso("NG");
    private static final Country GB = Country.byIso("GB");
    private static final Country US = Country.byIso("US");
    private static final Country IT = Country.byIso("IT");
    private static final Country RU = Country.byIso("RU");

    private static PhoneCleaner.Result r(String in, Country home) {
        return PhoneCleaner.clean(in, home);
    }

    private static String clean(String in, Country home) {
        return r(in, home).e164();
    }

    // ---- The ART / Cameroon guidelines ----

    @Test
    public void cameroonGuidelines() {
        // 1. Remove formatting (local format)
        assertEquals("677123456", r("677 12 34 56", CM).format(false, true));
        // 2. Add the country code
        assertEquals("+237677123456", clean("677123456", CM));
        // 3. Avoid duplication
        assertEquals("+237677123456", clean("+237 677 123 456", CM));
        assertEquals("+237677123456", clean("237 677 12 34 56", CM));
        // 5. Validate length: warn, don't change
        PhoneCleaner.Result tooLong = r("6771234567", CM);
        assertNotNull(tooLong.warning);
        assertEquals("+2376771234567", tooLong.e164());
        assertNotNull(r("+237 6771 2345", CM).warning);
        PhoneCleaner.Result shortWithCode = r("237 699 1234", CM);   // code typed, digits missing
        assertEquals("+2376991234", shortWithCode.e164());           // not "+237237…"
        assertNotNull(shortWithCode.warning);
        // 6. Fixed lines
        PhoneCleaner.Result fixed = r("222 12 34 56", CM);
        assertEquals("+237222123456", fixed.e164());
        assertEquals("Fixed line", fixed.lineType);
        assertNull(fixed.warning);
        assertEquals("Mobile", r("699 12 34 56", CM).lineType);
    }

    @Test
    public void cameroonMissingSix() {
        PhoneCleaner.Result a = r("237 77 12 34 56", CM);          // 237 + old 8-digit mobile
        assertEquals("+237677123456", a.e164());
        assertNotNull(a.fix);
        assertNull(a.warning);
        assertEquals("+237699123456", clean("+237 99 12 34 56", CM));
        assertEquals("+237677123456", clean("77 12 34 56", CM)); // local old format
        assertEquals("+237677123456", clean("0023777123456", NG)); // via 00, other home country
        assertEquals("+237222123456", clean("+237 22 12 34 56", CM)); // old fixed line → 2
        assertEquals("+237233123456", clean("+237 33 12 34 56", CM));
        // 8 digits starting with 6 is a truncated new number: warn, don't guess.
        PhoneCleaner.Result trunc = r("+237 67712345", CM);
        assertNull(trunc.fix);
        assertNotNull(trunc.warning);
    }

    @Test
    public void cameroonNetworks() {
        assertEquals("MTN", r("677 12 34 56", CM).network);
        assertEquals("MTN", r("+237 680 12 34 56", CM).network);
        assertEquals("MTN", r("650 12 34 56", CM).network);
        assertEquals("MTN", r("654 12 34 56", CM).network);
        assertEquals("Orange", r("699 12 34 56", CM).network);
        assertEquals("Orange", r("655 12 34 56", CM).network);
        assertEquals("Orange", r("659 12 34 56", CM).network);
        assertEquals("Nexttel", r("666 12 34 56", CM).network);
        assertEquals("Camtel", r("620 12 34 56", CM).network);
        assertEquals("Camtel", r("222 12 34 56", CM).network);
        assertEquals("MTN", r("237 77 12 34 56", CM).network);   // after adding the missing 6
        assertNull(r("640 12 34 56", CM).network);              // unallocated in our table: no guess
        assertNull(r("+44 20 7946 0958", CM).network);
    }

    @Test
    public void cameroonWrongPrefixWarns() {
        assertNotNull(r("+237 512345678", CM).warning);
    }

    // ---- Output formats ----

    @Test
    public void formats() {
        PhoneCleaner.Result x = r("(+237) 677-12-34-56", CM);
        assertEquals("+237677123456", x.format(true, true));
        assertEquals("237677123456", x.format(true, false));
        assertEquals("677123456", x.format(false, true));
        assertEquals("677123456", x.format(false, false));
        // Local format keeps the trunk prefix where the country uses one
        assertEquals("08031234567", r("+234 803 123 4567", CM).format(false, true));
        assertEquals("02079460958", r("+44 20 7946 0958", CM).format(false, true));
        assertEquals("5551234567", r("+1 555 123 4567", CM).format(false, true));
        // Unknown code can't be stripped
        assertEquals("+99912345678", r("+999 1234 5678", CM).format(false, true));
        assertFalse(r("+999 1234 5678", CM).canDropCode());
    }

    // ---- Country detection ----

    @Test
    public void detectsCountryFromPlus() {
        PhoneCleaner.Result x = r("+44 7946 095800", CM);
        assertEquals("GB", x.country.id);
        assertTrue(x.detected);
        assertEquals("NG", r("+2348031234567", CM).country.id);
        assertEquals("JM", r("+1 876 555 1234", CM).country.id);
        assertEquals("US", r("+1 212 555 1234", CM).country.id);
        assertEquals("CA", r("+1 416 555 1234", Country.byIso("CA")).country.id); // shared code → home
        assertEquals("KZ", r("+7 701 123 4567", CM).country.id);
        assertEquals("RU", r("+7 916 123 4567", CM).country.id);
        assertEquals("CM", r("+237677123456", NG).country.id);
    }

    @Test
    public void detectsCountryWithoutPlus() {
        PhoneCleaner.Result x = r("447946095800", CM);
        assertEquals("GB", x.country.id);
        assertEquals(PhoneCleaner.Kind.DETECTED, x.kind);
        assertEquals("+447946095800", x.e164());
        assertEquals("+2348031234567", clean("2348031234567", CM));
        assertEquals("+33612345678", clean("33612345678", CM));
        assertEquals("+237677123456", clean("237677123456", NG));
    }

    @Test
    public void homeCountryWinsForLocalNumbers() {
        assertEquals("+237677123456", clean("677123456", CM));
        assertEquals(PhoneCleaner.Kind.LOCAL, r("677123456", CM).kind);
        assertFalse(r("677123456", CM).detected);
    }

    @Test
    public void forcedCountryOverridesGuess() {
        // Without forcing, 11 digits starting 33 look French; forced to Nigeria it's a local number.
        assertEquals("FR", r("33612345678", CM).country.id);
        PhoneCleaner.Result f = PhoneCleaner.clean("08031234567", CM, NG);
        assertEquals("+2348031234567", f.e164());
        // "+" numbers keep their own code even when a country is forced
        assertEquals("GB", PhoneCleaner.clean("+44 20 7946 0958", CM, NG).country.id);
    }

    // ---- General cleaning (from v1) ----

    @Test
    public void dropsTrunkPrefix() {
        assertEquals("+2348031234567", clean("0803 123 4567", NG));
        assertEquals("+442079460958", clean("020 7946 0958", GB));
        assertEquals("+79161234567", clean("8 (916) 123-45-67", RU));
        assertEquals("+15551234567", clean("(555) 123-4567", US));
        assertEquals("+15551234567", clean("1 (555) 123-4567", US));
        assertEquals("+390612345678", clean("06 1234 5678", IT));
        assertEquals("+393921234567", clean("39 392 123 4567", IT));
        assertEquals("+393921234567", clean("392 123 4567", IT));
    }

    @Test
    public void removesExtraZeroAfterCode() {
        PhoneCleaner.Result x = r("+234 0803 123 4567", CM);
        assertEquals("+2348031234567", x.e164());
        assertNotNull(x.fix);
        assertEquals("+442079460958", clean("+44 (0)20 7946 0958", CM));
    }

    @Test
    public void internationalPrefix() {
        assertEquals("+237677123456", clean("00237 677 12 34 56", CM));
        assertEquals("+442079460958", clean("011 44 20 7946 0958", US));
        assertEquals(PhoneCleaner.Kind.EXIT_CONVERTED, r("00 44 20 7946 0958", CM).kind);
    }

    @Test
    public void stripsLabelsExtensionsAndJunk() {
        assertEquals("+237677123456", clean("tel:+237-677-12-34-56", CM));
        assertEquals("+237677123456", clean("Phone: +237 677 12 34 56.", CM));
        assertEquals("+15551234567", clean("(555) 123-4567 ext. 89", US));
        assertEquals("+15551234567", clean("555.123.4567 x89", US));
        assertEquals("+237677123456", clean("   +237‑677 12 34 56\n", CM));
        assertEquals("+237677123456", clean("＋２３７ ６７７ １２ ３４ ５６", CM));
        assertEquals("+237677123456", clean("+٢٣٧٦٧٧١٢٣٤٥٦", CM));
    }

    @Test
    public void shortCodesAndEmpty() {
        assertEquals("112", clean("112", CM));
        assertEquals(PhoneCleaner.Kind.SHORT_CODE, r("8100", CM).kind);
        assertTrue(r("", CM).isEmpty());
        assertTrue(r("  - () ", CM).isEmpty());
        assertTrue(r(null, CM).isEmpty());
        assertTrue(r("hello", CM).isEmpty());
    }

    @Test
    public void tooLongWarns() {
        assertNotNull(r("+1234567890123456", CM).warning);
    }

    @Test
    public void countryTable() {
        assertEquals(CM, Country.fromId("CM"));
        assertNull(Country.fromId("NONE"));
        assertNull(Country.fromId("CUSTOM:1876"));
        assertEquals("🇨🇲", CM.flag());
        assertTrue(Country.all().size() > 180);
        // Every entry parses to sane values
        for (Country c : Country.all()) {
            assertTrue(c.id, c.dialCode.matches("[1-9][0-9]{0,3}"));
            assertTrue(c.id, c.minLen <= c.maxLen);
        }
    }
}
