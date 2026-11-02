package app.numbercleaner;

/**
 * USSD code templates such as {@code *126*9*{number}*{amount}#} (MTN MoMo "send money").
 * Pure Java so it can be unit-tested.
 */
public final class Ussd {

    public static final String DEFAULT_TEMPLATE = "*126*9*{number}*{amount}#";
    public static final String NUMBER = "{number}";
    public static final String AMOUNT = "{amount}";

    private Ussd() {
    }

    /**
     * Must contain {number} and, once filled in, look like a USSD code: start with * or #, end with #,
     * dial-pad characters only. This stops a template from turning into an ordinary phone call.
     */
    public static boolean isValidTemplate(String template) {
        if (template == null || !template.contains(NUMBER)) return false;
        String filled = template.replace(NUMBER, "1").replace(AMOUNT, "1");
        return filled.matches("[*#][0-9*#]*#");
    }

    public static boolean needsAmount(String template) {
        return template != null && template.contains(AMOUNT);
    }

    /** Whole positive amounts only (USSD menus don't take decimals), up to 9 digits. */
    public static boolean isValidAmount(String amount) {
        return amount != null && amount.matches("[1-9][0-9]{0,8}");
    }

    /**
     * Fills the template. {@code number} should be the local format (e.g. 677123456).
     * Returns null when something required is missing.
     */
    public static String build(String template, String number, String amount) {
        if (!isValidTemplate(template) || number == null || !number.matches("\\+?[0-9]{3,15}")) return null;
        if (needsAmount(template) && !isValidAmount(amount)) return null;
        String code = template.replace(NUMBER, number);
        if (needsAmount(template)) code = code.replace(AMOUNT, amount);
        return code;
    }

    /** Same as {@link #build} but with "…" in place of a missing amount, for previews. */
    public static String preview(String template, String number, String amount) {
        if (!isValidTemplate(template) || number == null) return template;
        String a = isValidAmount(amount) ? amount : "…";
        return template.replace(NUMBER, number).replace(AMOUNT, a);
    }
}
