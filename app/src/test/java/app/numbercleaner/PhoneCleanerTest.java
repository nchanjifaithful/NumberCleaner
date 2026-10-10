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

    private static String clean(String in, Country c) {
        return PhoneCleaner.clean(in, c).number;
    }

    @Test
    public void addsCountryCodeToLocalNumber() {
        assertEquals("+237677123456", clean("677 12 34 56", CM));
        assertEquals("+237677123456", clean("677-12-34-56", CM));
        assertEquals("+237222234567", clean("(222) 23.45.67", CM));
        assertEquals(PhoneCleaner.Kind.ADDED_CODE, PhoneCleaner.clean("677123456", CM).kind);
    }

    @Test
    public void keepsInternationalNumbersAndPlus() {
        assertEquals("+237677123456", clean("+237 677 12 34 56", CM));
        assertEquals("+237677123456", clean("(+237) 677-12-34-56", CM));
        assertEquals("+447946095800", clean("+44 7946 095800", CM)); // different country stays as typed
        assertEquals(PhoneCleaner.Kind.INTERNATIONAL, PhoneCleaner.clean("+44 7946 095800", CM).kind);
    }

    @Test
    public void doesNotDuplicateExistingCode() {
        assertEquals("+237677123456", clean("237 677 12 34 56", CM));
        assertEquals("+237677123456", clean("237677123456", CM));
        assertEquals(PhoneCleaner.Kind.HAD_CODE, PhoneCleaner.clean("237677123456", CM).kind);
        assertEquals("+2348031234567", clean("234 803 123 4567", NG));
        assertEquals("+15551234567", clean("1 (555) 123-4567", US));
    }

    @Test
    public void localNumberThatHappensToStartWithCodeDigitsIsNotTreatedAsCoded() {
        // Italian mobiles can begin with 39; 10 digits is a valid national number on its own.
        assertEquals("+393921234567", clean("392 123 4567", IT));
        assertEquals("+393921234567", clean("39 392 123 4567", IT));
    }

    @Test
    public void dropsTrunkPrefix() {
        assertEquals("+2348031234567", clean("0803 123 4567", NG));
        assertEquals("+442079460958", clean("020 7946 0958", GB));
        assertEquals("+79161234567", clean("8 (916) 123-45-67", RU));
        assertEquals("+15551234567", clean("(555) 123-4567", US));
    }

    @Test
    public void italyKeepsLeadingZero() {
        assertEquals("+390612345678", clean("06 1234 5678", IT));
    }

    @Test
    public void convertsInternationalCallPrefix() {
        assertEquals("+237677123456", clean("00237 677 12 34 56", CM));
        assertEquals("+442079460958", clean("011 44 20 7946 0958", US));
        assertEquals(PhoneCleaner.Kind.EXIT_CONVERTED, PhoneCleaner.clean("00 44 20 7946 0958", CM).kind);
    }

    @Test
    public void removesBracketedTrunkZeroInInternationalFormat() {
        assertEquals("+442079460958", clean("+44 (0)20 7946 0958", CM));
    }

    @Test
    public void stripsLabelsExtensionsAndJunk() {
        assertEquals("+237677123456", clean("tel:+237-677-12-34-56", CM));
        assertEquals("+237677123456", clean("Phone: +237 677 12 34 56.", CM));
        assertEquals("+15551234567", clean("(555) 123-4567 ext. 89", US));
        assertEquals("+15551234567", clean("555.123.4567 x89", US));
        assertEquals("+237677123456", clean("   +237‑677 12 34 56\n", CM));
    }

    @Test
    public void fullWidthAndArabicDigits() {
        assertEquals("+237677123456", clean("＋２３７ ６７７ １２ ３４ ５６", CM));
        assertEquals("+237677123456", clean("+٢٣٧٦٧٧١٢٣٤٥٦", CM));
    }

    @Test
    public void noCountryCodeMeansDigitsOnly() {
        PhoneCleaner.Result r = PhoneCleaner.clean("677 12 34 56", Country.NONE);
        assertEquals("677123456", r.number);
        assertEquals(PhoneCleaner.Kind.DIGITS_ONLY, r.kind);
        assertEquals("+237677123456", clean("+237 677 12 34 56", Country.NONE));
    }

    @Test
    public void shortCodesAreLeftAlone() {
        assertEquals("112", clean("112", CM));
        assertEquals("8100", clean("8100", CM));
        assertEquals(PhoneCleaner.Kind.SHORT_CODE, PhoneCleaner.clean("117", CM).kind);
    }

    @Test
    public void alreadyCleanIsReportedUnchanged() {
        assertTrue(PhoneCleaner.clean("+237677123456", CM).unchanged);
        assertTrue(PhoneCleaner.clean(" +237677123456 ", CM).unchanged);
        assertFalse(PhoneCleaner.clean("+237 677123456", CM).unchanged);
    }

    @Test
    public void emptyInput() {
        assertTrue(PhoneCleaner.clean("", CM).isEmpty());
        assertTrue(PhoneCleaner.clean("  - () ", CM).isEmpty());
        assertTrue(PhoneCleaner.clean(null, CM).isEmpty());
        assertTrue(PhoneCleaner.clean("hello", CM).isEmpty());
    }

    @Test
    public void warnings() {
        assertNull(PhoneCleaner.clean("677 12 34 56", CM).warning);
        assertNotNull(PhoneCleaner.clean("677 12 34", CM).warning);          // 7 digits, Cameroon needs 9
        assertNotNull(PhoneCleaner.clean("+1234567890123456", CM).warning);  // > 15 digits
        assertNotNull(PhoneCleaner.clean("+12345", CM).warning);             // too short
    }

    @Test
    public void customCode() {
        Country jm = Country.custom("1876");
        assertEquals("+18765551234", clean("555 1234", jm)); // 7-digit local
        assertEquals("+2347012345678", clean("07012345678", Country.custom("234")));
        assertEquals("+2347012345678", clean("2347012345678", Country.custom("234")));
        assertTrue(Country.isValidCode("237"));
        assertFalse(Country.isValidCode("0237"));
        assertFalse(Country.isValidCode("12345"));
    }

    @Test
    public void countryIds() {
        assertEquals(CM, Country.fromId("CM"));
        assertEquals(Country.NONE, Country.fromId("NONE"));
        assertEquals("1876", Country.fromId("CUSTOM:1876").dialCode);
        assertNull(Country.fromId("XX"));
        assertEquals("🇨🇲", CM.flag());
    }
}
