package app.numbercleaner;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Small dialog: "Send money to 677123456 · Likely MTN", amount field, live preview of the exact code,
 * then Dial. Used from the app's Send button and the widget's Send button (so it opens over the
 * home screen without bringing up the main app).
 *
 * Dialling: opens the phone's dialer with the code filled in (no permission needed). If the user enabled
 * "Dial directly" and granted the phone permission, the code is called straight away instead.
 */
public class UssdActivity extends Activity {

    /** Stored (E.164) number to send to. */
    static final String EXTRA_NUMBER = "number";

    private Store store;
    private String template;
    private String localNumber;
    private EditText amount;
    private TextView code;
    private Button dial;
    /** Non-zero: this number can't be sent to (string resource explaining why). */
    private int blockReason;

    static Intent intent(Context context, String storedNumber) {
        return new Intent(context, UssdActivity.class).putExtra(EXTRA_NUMBER, storedNumber);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ussd);
        setFinishOnTouchOutside(true);
        store = new Store(this);
        template = store.ussdTemplate();

        String stored = getIntent().getStringExtra(EXTRA_NUMBER);
        PhoneCleaner.Result r = stored != null ? store.parse(stored) : null;
        if (r == null || r.isEmpty()) {
            Toast.makeText(this, R.string.ussd_no_number, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // USSD menus take the local number: 677123456, not +237677123456.
        localNumber = r.format(false, false);
        blockReason = store.sendBlockReason(r);

        ((TextView) findViewById(R.id.ussd_number)).setText(localNumber);
        TextView meta = findViewById(R.id.ussd_meta);
        meta.setText(meta(r));
        meta.setVisibility(r.country != null ? View.VISIBLE : View.GONE);
        TextView warning = findViewById(R.id.ussd_warning);
        String problem = r.warning;
        if (blockReason != 0) {
            String why = blockReason == R.string.ussd_foreign
                    ? getString(R.string.ussd_foreign, store.getCountry().name) : getString(blockReason);
            problem = problem != null ? problem + "\n" + why : why;
        }
        warning.setText(problem != null ? "⚠  " + problem : null);
        warning.setVisibility(problem != null ? View.VISIBLE : View.GONE);

        amount = findViewById(R.id.ussd_amount);
        code = findViewById(R.id.ussd_code);
        dial = findViewById(R.id.ussd_dial);
        findViewById(R.id.ussd_cancel).setOnClickListener(v -> finish());
        dial.setOnClickListener(v -> dial());

        boolean needsAmount = Ussd.needsAmount(template);
        findViewById(R.id.ussd_amount_label).setVisibility(needsAmount ? View.VISIBLE : View.GONE);
        amount.setVisibility(needsAmount ? View.VISIBLE : View.GONE);
        amount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        amount.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && dial.isEnabled()) {
                dial();
                return true;
            }
            return false;
        });
        if (needsAmount) {
            // Start from the saved default amount; selected so typing replaces it.
            String preset = store.defaultAmount();
            if (!preset.isEmpty()) {
                amount.setText(preset);
                amount.selectAll();
            }
            amount.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        refresh();
    }

    private String meta(PhoneCleaner.Result r) {
        if (r.country == null) return "";
        StringBuilder sb = new StringBuilder(r.country.flag()).append("  ");
        sb.append(r.network != null ? getString(R.string.likely_network, r.network) : r.country.name);
        if (r.lineType != null) sb.append(" · ").append(r.lineType);
        return sb.toString();
    }

    private void refresh() {
        String a = amount.getText().toString().trim();
        code.setText(Ussd.preview(template, localNumber, a));
        dial.setEnabled(blockReason == 0 && Ussd.build(template, localNumber, a) != null);
    }

    private void dial() {
        if (!dial.isEnabled() || blockReason != 0) return;
        dial.setEnabled(false);   // no double dial from a double tap / Done + tap
        String ussd = Ussd.build(template, localNumber, amount.getText().toString().trim());
        if (ussd != null && launch(this, store, ussd)) {
            store.remember(getIntent().getStringExtra(EXTRA_NUMBER));
            finish();
        } else {
            refresh();
        }
    }

    /**
     * Dials a finished USSD code: calls it directly when "Dial directly" is on and the phone permission
     * is granted, otherwise opens the dialer with the code filled in. Shared with the widget's instant Send.
     * @return false if nothing could handle it (a toast has been shown)
     */
    static boolean launch(Context context, Store store, String ussd) {
        // Uri.fromParts encodes '#' as %23, which dialers require for USSD.
        Uri uri = Uri.fromParts("tel", ussd, null);
        boolean direct = store.directDial()
                && context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        try {
            context.startActivity(new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (SecurityException e) {
            // Direct call refused: fall back to the dialer with the code filled in.
            try {
                context.startActivity(new Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, R.string.ussd_no_dialer, Toast.LENGTH_LONG).show();
                return false;
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.ussd_no_dialer, Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
