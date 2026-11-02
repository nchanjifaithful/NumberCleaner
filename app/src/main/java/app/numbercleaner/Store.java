package app.numbercleaner;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SharedPreferences-backed settings and history.
 * History stores canonical E.164 numbers; they are shown and copied in the current output format.
 */
public final class Store {

    public static final int MAX_HISTORY = 15;

    private static final String PREFS = "number_cleaner";
    private static final String KEY_COUNTRY = "country";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_INCLUDE_CODE = "include_code";
    private static final String KEY_INCLUDE_PLUS = "include_plus";
    private static final String KEY_WIDGET_NUMBER = "widget_number";
    private static final String KEY_AUTO_COPY = "widget_auto_copy";
    private static final String KEY_USSD_TEMPLATE = "ussd_template";
    private static final String KEY_DIRECT_DIAL = "ussd_direct_dial";
    private static final String KEY_DEFAULT_AMOUNT = "ussd_default_amount";
    private static final String KEY_ASK_AMOUNT = "ussd_widget_ask_amount";
    private static final String KEY_LAST_INSTANT_SEND = "ussd_last_instant_send";
    private static final long INSTANT_SEND_GAP_MS = 4000;

    public static final class Entry {
        public final String number;
        public final long time;

        Entry(String number, long time) {
            this.number = number;
            this.time = time;
        }
    }

    private final Context app;
    private final SharedPreferences prefs;

    public Store(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- Country ----

    public Country getCountry() {
        Country c = Country.fromId(prefs.getString(KEY_COUNTRY, null));
        return c != null ? c : guessCountry();
    }

    public void setCountry(Country c) {
        prefs.edit().putString(KEY_COUNTRY, c.id).apply();
    }

    /** SIM country, then network country, then device locale; Cameroon if nothing matches. */
    private Country guessCountry() {
        List<String> candidates = new ArrayList<>();
        try {
            TelephonyManager tm = (TelephonyManager) app.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                candidates.add(tm.getSimCountryIso());      // no permission required
                candidates.add(tm.getNetworkCountryIso());  // no permission required
            }
        } catch (RuntimeException ignored) {
            // Devices without telephony may throw; fall through to locale.
        }
        candidates.add(Locale.getDefault().getCountry());
        for (String iso : candidates) {
            Country c = Country.byIso(iso);
            if (c != null) return c;
        }
        return Country.byIso("CM");
    }

    // ---- Output format ----

    public boolean includeCode() {
        return prefs.getBoolean(KEY_INCLUDE_CODE, true);
    }

    public boolean includePlus() {
        return prefs.getBoolean(KEY_INCLUDE_PLUS, true);
    }

    public void setFormat(boolean includeCode, boolean includePlus) {
        prefs.edit().putBoolean(KEY_INCLUDE_CODE, includeCode).putBoolean(KEY_INCLUDE_PLUS, includePlus).commit();
        NumberWidget.updateAll(app);
    }

    /** Parses a stored number again (to know its country). */
    public PhoneCleaner.Result parse(String stored) {
        return PhoneCleaner.clean(stored, getCountry());
    }

    /** A stored number in the current output format — what Copy puts on the clipboard. */
    public String display(String stored) {
        return parse(stored).format(includeCode(), includePlus());
    }

    // ---- Settings ----

    /** Widget Paste also copies the cleaned number straight away. */
    public boolean autoCopyOnPaste() {
        return prefs.getBoolean(KEY_AUTO_COPY, false);
    }

    public void setAutoCopyOnPaste(boolean on) {
        prefs.edit().putBoolean(KEY_AUTO_COPY, on).apply();
    }

    public String ussdTemplate() {
        String t = prefs.getString(KEY_USSD_TEMPLATE, null);
        return Ussd.isValidTemplate(t) ? t : Ussd.DEFAULT_TEMPLATE;
    }

    public void setUssdTemplate(String template) {
        prefs.edit().putString(KEY_USSD_TEMPLATE, template).commit();
        NumberWidget.updateAll(app);   // the widget's "Send 5k" label depends on it
    }

    /** Call the code immediately (needs the phone permission) instead of opening the dialer. */
    public boolean directDial() {
        return prefs.getBoolean(KEY_DIRECT_DIAL, false);
    }

    public void setDirectDial(boolean on) {
        prefs.edit().putBoolean(KEY_DIRECT_DIAL, on).apply();
    }

    /**
     * Why this number can't be used with the USSD code, or 0 if it can. Money codes take a local
     * number of your own country, so unknown/foreign numbers and numbers with a warning are refused.
     */
    public int sendBlockReason(PhoneCleaner.Result r) {
        if (r == null || r.isEmpty() || r.kind == PhoneCleaner.Kind.SHORT_CODE) return R.string.ussd_no_number;
        if (r.country == null || r.country != getCountry()) return R.string.ussd_foreign;
        if (r.warning != null) return R.string.ussd_check_number;
        return 0;
    }

    /** Amount the widget's Send uses without asking ("" = none set, so it asks). */
    public String defaultAmount() {
        String a = prefs.getString(KEY_DEFAULT_AMOUNT, "");
        return Ussd.isValidAmount(a) ? a : "";
    }

    public void setDefaultAmount(String amount) {
        prefs.edit().putString(KEY_DEFAULT_AMOUNT, Ussd.isValidAmount(amount) ? amount : "").commit();
        NumberWidget.updateAll(app);
    }

    /** Widget Send shows the amount dialog every time instead of dialling with the default amount. */
    public boolean askAmountInWidget() {
        return prefs.getBoolean(KEY_ASK_AMOUNT, false);
    }

    public void setAskAmountInWidget(boolean ask) {
        prefs.edit().putBoolean(KEY_ASK_AMOUNT, ask).commit();
        NumberWidget.updateAll(app);
    }

    /** True when the widget's Send can dial straight away: amount known (or not needed) and not set to ask. */
    public boolean widgetSendsInstantly() {
        if (askAmountInWidget()) return false;
        return !Ussd.needsAmount(ussdTemplate()) || !defaultAmount().isEmpty();
    }

    /**
     * Guards the widget's instant Send against double taps: returns false if another instant send
     * happened in the last few seconds, otherwise records this one and returns true.
     */
    public boolean claimInstantSend() {
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_INSTANT_SEND, 0);
        if (now - last >= 0 && now - last < INSTANT_SEND_GAP_MS) return false;
        prefs.edit().putLong(KEY_LAST_INSTANT_SEND, now).commit();
        return true;
    }

    // ---- Widget ----

    /** Number in the widget's box: the one pasted there, else the last copied one. May be null. */
    public String getWidgetNumber() {
        String w = prefs.getString(KEY_WIDGET_NUMBER, null);
        return w != null ? w : getLast();
    }

    /** Puts a freshly pasted number in the widget's box (not copied yet, so not in recents). */
    public void setWidgetNumber(String number) {
        prefs.edit().putString(KEY_WIDGET_NUMBER, number).commit();
        NumberWidget.updateAll(app);
    }

    // ---- History ----

    public List<Entry> getHistory() {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.getString("n"), o.optLong("t")));
            }
        } catch (JSONException e) {
            // Corrupt data: start over.
        }
        return out;
    }

    /** Most recent number (canonical form), or null. */
    public String getLast() {
        List<Entry> h = getHistory();
        return h.isEmpty() ? null : h.get(0).number;
    }

    /** Puts a number at the top of history (moving it if already there) and refreshes the widget. */
    public void remember(String number) {
        if (number == null || number.isEmpty()) return;
        List<Entry> h = getHistory();
        for (int i = h.size() - 1; i >= 0; i--) {
            if (h.get(i).number.equals(number)) h.remove(i);
        }
        h.add(0, new Entry(number, System.currentTimeMillis()));
        while (h.size() > MAX_HISTORY) h.remove(h.size() - 1);
        save(h);
        // The widget box follows the latest copy, wherever it happened.
        prefs.edit().remove(KEY_WIDGET_NUMBER).commit();
        NumberWidget.updateAll(app);
    }

    public void remove(String number) {
        List<Entry> h = getHistory();
        for (int i = h.size() - 1; i >= 0; i--) {
            if (h.get(i).number.equals(number)) h.remove(i);
        }
        save(h);
        NumberWidget.updateAll(app);
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).commit();
        NumberWidget.updateAll(app);
    }

    private void save(List<Entry> h) {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : h) arr.put(new JSONObject().put("n", e.number).put("t", e.time));
        } catch (JSONException ignored) {
            // put() with String/long cannot fail.
        }
        // commit() so the widget, updated right after, reads the new value.
        prefs.edit().putString(KEY_HISTORY, arr.toString()).commit();
    }
}
