package app.numbercleaner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home-screen widget — everything works without opening the app.
 * <pre>
 *  small   [ +237677123456 ] (paste)(copy)(send)
 *
 *  medium  🇨🇲 Likely MTN · Mobile        (✓ 237)(✓ +)
 *          [ +237677123456                         ]
 *          [ Paste ]   [ Copy ]   [ Send ]
 *
 *  large   medium + "Recent" with up to 3 numbers (tap to copy)
 * </pre>
 * Empty state: the box explains what to do and only Paste is shown (full width).
 * Paste reads the clipboard through an invisible activity (Android 10+ only lets a focused app read it).
 * Send opens the small send-money dialog over the home screen.
 */
public class NumberWidget extends AppWidgetProvider {

    static final String ACTION_COPY = "app.numbercleaner.action.WIDGET_COPY";
    static final String ACTION_TOGGLE_CODE = "app.numbercleaner.action.WIDGET_TOGGLE_CODE";
    static final String ACTION_TOGGLE_PLUS = "app.numbercleaner.action.WIDGET_TOGGLE_PLUS";
    static final String EXTRA_NUMBER = "number";

    private static final int[] ROWS = {R.id.w_recent_1, R.id.w_recent_2, R.id.w_recent_3};
    private static final int[] ROW_TEXT = {R.id.w_recent_1_text, R.id.w_recent_2_text, R.id.w_recent_3_text};
    private static final int[] ROW_META = {R.id.w_recent_1_meta, R.id.w_recent_2_meta, R.id.w_recent_3_meta};

    /** Heights (dp) each layout needs — must match the XML. */
    private static final float SMALL_H = 40f;
    private static final float MEDIUM_H = 150f;        // 24 padding + 26 header + 6 + 46 box + 8 + 40 buttons
    private static final float LARGE_BASE_H = 180f;    // medium + 10 + 20 "Recent" label
    private static final float ROW_H = 36f;

    // PendingIntent request codes (one per distinct target)
    private static final int RC_PASTE = 1, RC_COPY = 2, RC_OPEN = 3, RC_CODE = 4, RC_PLUS = 5, RC_SEND = 6, RC_ROW = 10;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        render(context, manager, id);   // resized: maybe switch layout
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Store store = new Store(context);
        if (ACTION_COPY.equals(action)) {
            String stored = intent.getStringExtra(EXTRA_NUMBER);
            if (stored == null) stored = store.getWidgetNumber();
            if (stored != null) {
                Clip.copy(context, store.display(stored));
                store.remember(stored);   // adds to recents, puts it in the box, refreshes widgets
            }
        } else if (ACTION_TOGGLE_CODE.equals(action)) {
            store.setFormat(!store.includeCode(), store.includePlus());
        } else if (ACTION_TOGGLE_PLUS.equals(action)) {
            if (store.includeCode()) store.setFormat(true, !store.includePlus());
        } else {
            super.onReceive(context, intent);
        }
    }

    /** Re-renders every placed instance; no-op when none are placed. */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NumberWidget.class));
        if (ids == null) return;
        for (int id : ids) render(context, manager, id);
    }

    private static void render(Context context, AppWidgetManager manager, int id) {
        Store store = new Store(context);
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+: the launcher switches layouts itself as the widget is resized.
            Map<SizeF, RemoteViews> sizes = new HashMap<>();
            sizes.put(new SizeF(140f, SMALL_H), build(context, store, Size.SMALL, 0));
            sizes.put(new SizeF(140f, MEDIUM_H), build(context, store, Size.MEDIUM, 0));
            for (int rows = 1; rows <= ROWS.length; rows++) {
                sizes.put(new SizeF(140f, LARGE_BASE_H + ROW_H * rows), build(context, store, Size.LARGE, rows));
            }
            manager.updateAppWidget(id, new RemoteViews(sizes));
        } else {
            // Older launchers: pick from the reported size (portrait height = MAX_HEIGHT on most).
            Bundle o = manager.getAppWidgetOptions(id);
            int h = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : 0;
            int rows = 0;
            while (rows < ROWS.length && h >= LARGE_BASE_H + ROW_H * (rows + 1)) rows++;
            Size size = rows > 0 ? Size.LARGE : h >= MEDIUM_H ? Size.MEDIUM : Size.SMALL;
            manager.updateAppWidget(id, build(context, store, size, rows));
        }
    }

    private enum Size { SMALL, MEDIUM, LARGE }

    private static RemoteViews build(Context context, Store store, Size size, int rows) {
        int layout = size == Size.SMALL ? R.layout.widget_small
                : size == Size.MEDIUM ? R.layout.widget_medium : R.layout.widget_large;
        RemoteViews v = new RemoteViews(context.getPackageName(), layout);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        boolean code = store.includeCode();
        boolean plus = store.includePlus();
        String number = store.getWidgetNumber();
        PhoneCleaner.Result r = number != null ? store.parse(number) : null;
        boolean has = r != null && !r.isEmpty();

        // ---- Box + actions (all sizes)
        v.setTextViewText(R.id.w_number, has ? r.format(code, plus) : context.getString(R.string.widget_empty));
        PendingIntent paste = PendingIntent.getActivity(context, RC_PASTE,
                new Intent(context, QuickCleanActivity.class)
                        .putExtra(QuickCleanActivity.EXTRA_TO_WIDGET, true)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION), flags);
        v.setOnClickPendingIntent(R.id.w_paste, paste);

        // Empty: only Paste (it takes the whole row); the box also pastes.
        v.setViewVisibility(R.id.w_copy, has ? View.VISIBLE : View.GONE);
        boolean canSend = has && store.sendBlockReason(r) == 0;
        v.setViewVisibility(R.id.w_send, canSend ? View.VISIBLE : View.GONE);
        if (has) {
            PendingIntent copy = broadcast(context, new Intent(context, NumberWidget.class).setAction(ACTION_COPY), RC_COPY, flags);
            v.setOnClickPendingIntent(R.id.w_copy, copy);
            v.setOnClickPendingIntent(R.id.w_box, copy);
        } else {
            v.setOnClickPendingIntent(R.id.w_box, paste);
        }
        if (canSend) {
            Intent send = UssdActivity.intent(context, number)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            v.setOnClickPendingIntent(R.id.w_send, PendingIntent.getActivity(context, RC_SEND, send, flags));
        }
        if (size == Size.SMALL) return v;

        // ---- Header: country / network (or warning), and format toggles
        String warning = has ? r.warning : null;
        v.setViewVisibility(R.id.w_meta, warning == null ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.w_warn, warning == null ? View.GONE : View.VISIBLE);
        if (warning != null) v.setTextViewText(R.id.w_warn, "⚠ " + warning);
        else v.setTextViewText(R.id.w_meta, meta(context, has ? r : null));
        Intent open = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, number != null ? number : "")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.w_meta_row, PendingIntent.getActivity(context, RC_OPEN, open, flags));

        // The code chip shows the actual code it adds ("✓ 237"), so the effect is obvious.
        String cc = has && r.country != null ? r.country.dialCode : store.getCountry().dialCode;
        v.setTextViewText(R.id.w_code, context.getString(code ? R.string.widget_code_on : R.string.widget_code_off, cc));
        v.setInt(R.id.w_code, "setBackgroundResource", code ? R.drawable.bg_wchip_on : R.drawable.bg_wchip_off);
        v.setTextViewText(R.id.w_plus, context.getString(code && plus ? R.string.widget_plus_on : R.string.widget_plus_off));
        v.setInt(R.id.w_plus, "setBackgroundResource",
                !code ? R.drawable.bg_wchip_disabled : plus ? R.drawable.bg_wchip_on : R.drawable.bg_wchip_off);
        v.setOnClickPendingIntent(R.id.w_code,
                broadcast(context, new Intent(context, NumberWidget.class).setAction(ACTION_TOGGLE_CODE), RC_CODE, flags));
        v.setOnClickPendingIntent(R.id.w_plus,
                broadcast(context, new Intent(context, NumberWidget.class).setAction(ACTION_TOGGLE_PLUS), RC_PLUS, flags));
        if (size == Size.MEDIUM) return v;

        // ---- Recent numbers other than the one in the box
        List<String> recent = new ArrayList<>();
        for (Store.Entry e : store.getHistory()) {
            if (!e.number.equals(number)) recent.add(e.number);
        }
        for (int i = 0; i < ROWS.length; i++) {
            if (i < rows && i < recent.size()) {
                String stored = recent.get(i);
                PhoneCleaner.Result rr = store.parse(stored);
                v.setViewVisibility(ROWS[i], View.VISIBLE);
                v.setTextViewText(ROW_TEXT[i], rr.format(code, plus));
                v.setTextViewText(ROW_META[i], rr.network != null ? rr.network
                        : rr.country != null ? rr.country.flag() : "");
                Intent copy = new Intent(context, NumberWidget.class).setAction(ACTION_COPY).putExtra(EXTRA_NUMBER, stored);
                v.setOnClickPendingIntent(ROWS[i], broadcast(context, copy, RC_ROW + i, flags));
            } else {
                v.setViewVisibility(ROWS[i], View.GONE);
            }
        }
        return v;
    }

    /** "🇨🇲 Likely MTN · Mobile", "🇬🇧 United Kingdom", or a hint when empty. */
    private static String meta(Context context, PhoneCleaner.Result r) {
        if (r == null) return context.getString(R.string.widget_hint);
        if (r.country == null) return context.getString(R.string.app_name);
        StringBuilder sb = new StringBuilder(r.country.flag()).append("  ");
        sb.append(r.network != null ? context.getString(R.string.likely_network, r.network) : r.country.name);
        if (r.lineType != null) sb.append(" · ").append(r.lineType);
        return sb.toString();
    }

    private static PendingIntent broadcast(Context context, Intent intent, int requestCode, int flags) {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
