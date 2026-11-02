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

    /** Widget paste: put the cleaned number in the widget's box instead of copying it straight away. */
    static final String EXTRA_TO_WIDGET = "to_widget";
    /** Widget Send: dial with the saved amount, or open the amount dialog when that's needed. */
    static final String EXTRA_SEND = "send";
    /** With EXTRA_SEND: always show the amount dialog (the 1-row widget has no room to show the amount). */
    static final String EXTRA_ASK = "ask";

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
        if (savedInstanceState != null) {
            // Recreated (e.g. rotated) before finishing: never re-run, and don't linger invisibly.
            done = true;
            finish();
            return;
        }
        if (!done && getIntent().getBooleanExtra(EXTRA_SEND, false)) {
            done = true;   // Send doesn't need the clipboard, so no need to wait for focus
            send();
            finish();
            overridePendingTransition(0, 0);
        }
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

    /**
     * Widget Send. Dials immediately with the saved amount unless the user wants to be asked, no default
     * amount is set, or the number can't be used — then the amount dialog opens (it explains any problem).
     */
    private void send() {
        Store store = new Store(this);
        String number = store.getWidgetNumber();
        PhoneCleaner.Result r = number != null ? store.parse(number) : null;
        String template = store.ussdTemplate();
        boolean ask = getIntent().getBooleanExtra(EXTRA_ASK, false);
        String code = !ask && r != null && store.sendBlockReason(r) == 0 && store.widgetSendsInstantly()
                ? Ussd.build(template, r.format(false, false), store.defaultAmount()) : null;
        if (code == null) {
            if (number != null) {
                startActivity(UssdActivity.intent(this, number).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return;
        }
        if (!store.claimInstantSend()) return;   // second tap within a few seconds: ignore, no double payment
        if (UssdActivity.launch(this, store, code)) {
            store.remember(number);
        }
    }

    private void run() {
        String text = Clip.read(this);
        Store store = new Store(this);
        PhoneCleaner.Result r = Clip.looksLikeNumber(text) ? PhoneCleaner.clean(text, store.getCountry()) : null;
        if (getIntent().getBooleanExtra(EXTRA_TO_WIDGET, false)) {
            if (r == null || r.isEmpty()) {
                android.widget.Toast.makeText(this, R.string.widget_nothing, android.widget.Toast.LENGTH_SHORT).show();
            } else if (store.autoCopyOnPaste()) {
                // Setting on: paste = clean + copy in one tap (also adds to recents and fills the box).
                String out = r.format(store.includeCode(), store.includePlus());
                Clip.copy(this, out, r.warning != null ? getString(R.string.quick_copied_warn, out, r.warning)
                        : Build.VERSION.SDK_INT < 33 ? getString(R.string.quick_copied, out) : null);
                store.remember(r.e164());
            } else {
                store.setWidgetNumber(r.e164());
            }
            return;
        }
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
