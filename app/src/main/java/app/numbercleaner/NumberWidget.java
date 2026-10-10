package app.numbercleaner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

/** Home-screen widget: last cleaned number, a copy button and a "clean a new number" shortcut. */
public class NumberWidget extends AppWidgetProvider {

    static final String ACTION_COPY = "app.numbercleaner.action.WIDGET_COPY";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, manager, ids);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_COPY.equals(intent.getAction())) {
            String last = new Store(context).getLast();
            if (last != null) Clip.copy(context, last);
            return;
        }
        super.onReceive(context, intent);
    }

    /** Re-renders every placed instance; cheap no-op when none are placed. */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NumberWidget.class));
        if (ids != null && ids.length > 0) render(context, manager, ids);
    }

    private static void render(Context context, AppWidgetManager manager, int[] ids) {
        String last = new Store(context).getLast();
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_number);

        boolean has = last != null;
        v.setTextViewText(R.id.widget_number, has ? last : context.getString(R.string.widget_empty));
        v.setTextViewText(R.id.widget_label, context.getString(
                has ? R.string.widget_label : R.string.app_name));
        v.setViewVisibility(R.id.widget_copy, has ? View.VISIBLE : View.GONE);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent copy = new Intent(context, NumberWidget.class).setAction(ACTION_COPY);
        v.setOnClickPendingIntent(R.id.widget_copy, PendingIntent.getBroadcast(context, 1, copy, flags));

        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_text, PendingIntent.getActivity(context, 2, open, flags));

        Intent fresh = new Intent(context, MainActivity.class)
                .setAction(MainActivity.ACTION_CLEAN_NEW)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_new, PendingIntent.getActivity(context, 3, fresh, flags));

        manager.updateAppWidget(ids, v);
    }
}
