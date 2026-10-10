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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home-screen widget.
 * Compact (1 row): last copied number · copy · ✨ clean clipboard.
 * Tall (2+ rows): the same, plus up to 3 more recent numbers — tap one to copy it.
 * Numbers are shown and copied in the format chosen in the app (with/without + and country code).
 */
public class NumberWidget extends AppWidgetProvider {

    static final String ACTION_COPY = "app.numbercleaner.action.WIDGET_COPY";
    static final String EXTRA_NUMBER = "number";

    private static final int[] ROWS = {R.id.widget_recent_1, R.id.widget_recent_2, R.id.widget_recent_3};
    private static final int[] ROW_TEXT = {
            R.id.widget_recent_1_text, R.id.widget_recent_2_text, R.id.widget_recent_3_text};

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
        if (ACTION_COPY.equals(intent.getAction())) {
            Store store = new Store(context);
            String stored = intent.getStringExtra(EXTRA_NUMBER);
            if (stored == null) stored = store.getLast();
            if (stored != null) {
                String out = store.display(stored);
                Clip.copy(context, out);
                store.remember(stored);   // moves it to the top and refreshes widgets
            }
            return;
        }
        super.onReceive(context, intent);
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
        List<Store.Entry> history = store.getHistory();
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+: the launcher picks the layout that fits as the user resizes.
            Map<SizeF, RemoteViews> sizes = new HashMap<>();
            for (int rows = 0; rows <= ROWS.length; rows++) {
                sizes.put(new SizeF(160f, rows == 0 ? 40f : heightFor(rows)), build(context, store, history, rows));
            }
            manager.updateAppWidget(id, new RemoteViews(sizes));
        } else {
            // Portrait height is reported as MAX_HEIGHT on most launchers.
            Bundle o = manager.getAppWidgetOptions(id);
            int h = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : 0;
            int rows = 0;
            while (rows < ROWS.length && h >= heightFor(rows + 1)) rows++;
            manager.updateAppWidget(id, build(context, store, history, rows));
        }
    }

    /** dp needed by widget_large.xml with {@code rows} recent rows: padding + header + divider + rows. */
    private static float heightFor(int rows) {
        return 16 + 56 + 9 + 40 * rows + 4;
    }

    /** @param rows recent rows that fit below the header; 0 = compact one-row layout. */
    private static RemoteViews build(Context context, Store store, List<Store.Entry> history, int rows) {
        boolean tall = rows > 0;
        RemoteViews v = new RemoteViews(context.getPackageName(),
                tall ? R.layout.widget_large : R.layout.widget_number);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        String last = history.isEmpty() ? null : history.get(0).number;
        v.setTextViewText(R.id.widget_number, last != null ? store.display(last) : context.getString(R.string.widget_empty));
        v.setTextViewText(R.id.widget_label, context.getString(last != null ? R.string.widget_label : R.string.app_name));
        v.setViewVisibility(R.id.widget_copy, last != null ? View.VISIBLE : View.GONE);
        if (last != null) {
            v.setOnClickPendingIntent(R.id.widget_copy, copyIntent(context, last, 1, flags));
        }

        Intent clean = new Intent(context, QuickCleanActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        v.setOnClickPendingIntent(R.id.widget_clean, PendingIntent.getActivity(context, 2, clean, flags));

        Intent open = new Intent(context, MainActivity.class)
                .setAction(MainActivity.ACTION_CLEAN_NEW)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_text, PendingIntent.getActivity(context, 3, open, flags));

        if (tall) {
            for (int i = 0; i < ROWS.length; i++) {
                int index = i + 1;   // row 0 is the header
                if (i < rows && index < history.size()) {
                    String stored = history.get(index).number;
                    v.setViewVisibility(ROWS[i], View.VISIBLE);
                    v.setTextViewText(ROW_TEXT[i], store.display(stored));
                    v.setOnClickPendingIntent(ROWS[i], copyIntent(context, stored, 10 + i, flags));
                } else {
                    v.setViewVisibility(ROWS[i], View.GONE);
                }
            }
            v.setViewVisibility(R.id.widget_no_more, history.size() <= 1 ? View.VISIBLE : View.GONE);
        }
        return v;
    }

    private static PendingIntent copyIntent(Context context, String stored, int requestCode, int flags) {
        Intent copy = new Intent(context, NumberWidget.class)
                .setAction(ACTION_COPY)
                .putExtra(EXTRA_NUMBER, stored);
        return PendingIntent.getBroadcast(context, requestCode, copy, flags);
    }
}
