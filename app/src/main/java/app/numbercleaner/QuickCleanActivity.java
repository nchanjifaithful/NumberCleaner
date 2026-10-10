package app.numbercleaner;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Invisible one-shot screen behind the widget's ✨ button and the "Clean clipboard" launcher shortcut:
 * reads the clipboard, cleans the number, copies it back in the chosen format and adds it to recents.
 * It has to be an activity because Android 10+ only lets the focused app read the clipboard.
 */
public class QuickCleanActivity extends Activity {

    private boolean done;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** If focus never arrives (another window holds it), don't sit invisibly on top of the screen. */
    private final Runnable giveUp = () -> {
        if (!done) {
            done = true;
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) done = true;   // never re-run after a configuration change
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        run();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(giveUp, 1500);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(giveUp);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If focus never arrived (e.g. the screen locked), don't linger.
        if (!done) finish();
    }

    private void run() {
        String text = Clip.read(this);
        Store store = new Store(this);
        PhoneCleaner.Result r = Clip.looksLikeNumber(text) ? PhoneCleaner.clean(text, store.getCountry()) : null;
        if (r == null || r.isEmpty()) {
            // Nothing usable on the clipboard: open the app so the user can type one.
            startActivity(new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_CLEAN_NEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return;
        }
        String out = r.format(store.includeCode(), store.includePlus());
        // Android 13+ already confirms copies; only add a toast there when something needs checking.
        String toast = r.warning != null ? getString(R.string.quick_copied_warn, out, r.warning)
                : Build.VERSION.SDK_INT < 33 ? getString(R.string.quick_copied, out) : null;
        Clip.copy(this, out, toast);
        store.remember(r.e164());
    }
}
