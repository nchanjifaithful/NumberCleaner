package app.numbercleaner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UssdTest {

    private static final String T = Ussd.DEFAULT_TEMPLATE;

    @Test
    public void buildsMomoCode() {
        assertEquals("*126*9*677123456*5000#", Ussd.build(T, "677123456", "5000"));
    }

    @Test
    public void localNumberFromCleanedResult() {
        PhoneCleaner.Result r = PhoneCleaner.clean("(+237) 677-12-34-56", Country.byIso("CM"));
        assertEquals("*126*9*677123456*250#", Ussd.build(T, r.format(false, false), "250"));
    }

    @Test
    public void rejectsBadAmounts() {
        assertNull(Ussd.build(T, "677123456", ""));
        assertNull(Ussd.build(T, "677123456", "0"));
        assertNull(Ussd.build(T, "677123456", "05"));
        assertNull(Ussd.build(T, "677123456", "12.5"));
        assertNull(Ussd.build(T, "677123456", "1234567890"));
        assertNull(Ussd.build(T, "677123456", null));
    }

    @Test
    public void templates() {
        assertTrue(Ussd.isValidTemplate("*126*9*{number}*{amount}#"));
        assertTrue(Ussd.isValidTemplate("#150*1*1*{number}*{amount}#"));
        assertTrue(Ussd.isValidTemplate("*123*{number}#"));
        assertFalse(Ussd.isValidTemplate("*126*9*{amount}#"));        // no number
        assertFalse(Ussd.isValidTemplate("*126*9*{number}*abc#"));
        assertFalse(Ussd.isValidTemplate("{number}"));
        assertFalse(Ussd.isValidTemplate(null));
        // Must look like a USSD code, never an ordinary call
        assertFalse(Ussd.isValidTemplate("0{number}"));
        assertFalse(Ussd.isValidTemplate("*126*9*{number}*{amount}"));   // missing final #
        assertFalse(Ussd.isValidTemplate("126*9*{number}*{amount}#"));   // missing leading *
        assertFalse(Ussd.isValidTemplate("+*126*{number}#"));
        assertFalse(Ussd.needsAmount("*123*{number}#"));
        assertEquals("*123*677123456#", Ussd.build("*123*{number}#", "677123456", null));
    }

    @Test
    public void preview() {
        assertEquals("*126*9*677123456*…#", Ussd.preview(T, "677123456", ""));
        assertEquals("*126*9*677123456*900#", Ussd.preview(T, "677123456", "900"));
    }

    @Test
    public void rejectsBadNumbers() {
        assertNull(Ussd.build(T, "", "100"));
        assertNull(Ussd.build(T, "67#12", "100"));
    }
}
