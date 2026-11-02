package app.numbercleaner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    /** Sent by the widget: clear the field and offer the clipboard. */
    static final String ACTION_CLEAN_NEW = "app.numbercleaner.action.CLEAN_NEW";

    private static final String STATE_AUTO_PASTE = "auto_paste";
    private static final String STATE_FORCED = "forced";
    private static final int REQUEST_CALL_PHONE = 1;

    private Store store;
    /** Default country for local numbers. */
    private Country home;
    /** Country the user picked for the number currently in the field (overrides guessing). */
    private Country forced;
    private PhoneCleaner.Result current;

    private EditText input;
    private ImageButton pasteButton;
    private ImageButton clearButton;
    private TextView countryButton;
    private TextView output;
    private TextView meta;
    private TextView status;
    private TextView fixView;
    private TextView warningView;
    private CheckBox includeCode;
    private CheckBox includePlus;
    private Button copyButton;
    private ImageButton shareButton;
    private Button sendButton;
    private CheckBox autoCopySetting;
    private EditText ussdSetting;
    private TextView ussdHelp;
    private CheckBox directDialSetting;
    private LinearLayout historyList;
    private TextView historyEmpty;
    private Button clearHistoryButton;

    private boolean autoPastePending;
    private boolean flashTopRow;
    private AlertDialog dialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable resetCopyLabel = () -> copyButton.setText(R.string.copy);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new Store(this);
        home = store.getCountry();

        input = findViewById(R.id.input);
        pasteButton = findViewById(R.id.paste);
        clearButton = findViewById(R.id.clear);
        countryButton = findViewById(R.id.country);
        output = findViewById(R.id.output);
        meta = findViewById(R.id.meta);
        status = findViewById(R.id.status);
        fixView = findViewById(R.id.fix);
        warningView = findViewById(R.id.warning);
        includeCode = findViewById(R.id.include_code);
        includePlus = findViewById(R.id.include_plus);
        copyButton = findViewById(R.id.copy);
        shareButton = findViewById(R.id.share);
        sendButton = findViewById(R.id.send);
        autoCopySetting = findViewById(R.id.setting_auto_copy);
        ussdSetting = findViewById(R.id.setting_ussd);
        ussdHelp = findViewById(R.id.setting_ussd_help);
        directDialSetting = findViewById(R.id.setting_direct_dial);
        historyList = findViewById(R.id.history_list);
        historyEmpty = findViewById(R.id.history_empty);
        clearHistoryButton = findViewById(R.id.clear_history);

        // Phone keypad, but keep the plain text key listener so pasted text isn't filtered char-by-char.
        input.setRawInputType(InputType.TYPE_CLASS_PHONE);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 0) forced = null;   // a new number starts with auto-detection again
                refresh();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        pasteButton.setOnClickListener(v -> pasteFromClipboard(true));
        clearButton.setOnClickListener(v -> {
            input.setText("");
            showKeyboard();
        });
        countryButton.setOnClickListener(v -> openCountryPicker());
        meta.setOnClickListener(v -> openCountryPicker());

        // Format options: restored from settings, not from view state.
        includeCode.setSaveEnabled(false);
        includePlus.setSaveEnabled(false);
        includeCode.setChecked(store.includeCode());
        includePlus.setChecked(store.includePlus());
        includeCode.setOnCheckedChangeListener((b, checked) -> onFormatChanged());
        includePlus.setOnCheckedChangeListener((b, checked) -> onFormatChanged());

        copyButton.setOnClickListener(v -> copyCurrent());
        output.setOnClickListener(v -> copyCurrent());
        shareButton.setOnClickListener(v -> shareCurrent());
        sendButton.setOnClickListener(v -> sendCurrent());
        setupSettings();
        clearHistoryButton.setOnClickListener(v -> confirmClearHistory());

        if (savedInstanceState != null) {
            autoPastePending = savedInstanceState.getBoolean(STATE_AUTO_PASTE);
            forced = Country.fromId(savedInstanceState.getString(STATE_FORCED));
        } else if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            // Reopened from Recents: don't replay an old share / "clean new" request.
            handleIntent(getIntent());
        }
        renderCountryButton();
        updatePlusEnabled();
        refresh();
        renderHistory();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        if (hasWindowFocus()) runAutoPaste();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_AUTO_PASTE, autoPastePending);
        if (forced != null) out.putString(STATE_FORCED, forced.id);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Android 10+ only lets the focused app read the clipboard, so wait for focus.
        if (hasFocus) runAutoPaste();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed in another window of the app (e.g. one opened via Share).
        Country stored = store.getCountry();
        if (stored != home) {
            home = stored;
            renderCountryButton();
        }
        if (includeCode.isChecked() != store.includeCode() || includePlus.isChecked() != store.includePlus()) {
            includeCode.setOnCheckedChangeListener(null);
            includePlus.setOnCheckedChangeListener(null);
            includeCode.setChecked(store.includeCode());
            includePlus.setChecked(store.includePlus());
            includeCode.setOnCheckedChangeListener((b, checked) -> onFormatChanged());
            includePlus.setOnCheckedChangeListener((b, checked) -> onFormatChanged());
            updatePlusEnabled();
        }
        syncDirectDial();    // permission may have been changed in system settings
        refresh();
        renderHistory();   // the widget may have changed it
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ussdSetting.hasFocus()) commitUssdTemplate();
    }

    private boolean dialogOpen() {
        return dialog != null && dialog.isShowing();
    }

    @Override
    protected void onDestroy() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        handler.removeCallbacks(resetCopyLabel);
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        CharSequence text = null;
        if (Intent.ACTION_SEND.equals(action)) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        } else if (ACTION_CLEAN_NEW.equals(action)) {
            if (dialogOpen()) dialog.dismiss();   // auto-paste needs the window focus
            input.setText("");
            autoPastePending = true;
        }
        if (text != null) setInput(text.toString());
    }

    private void setInput(String text) {
        forced = null;
        input.setText(text);
        input.setSelection(input.length());
    }

    private void runAutoPaste() {
        if (!autoPastePending) return;
        autoPastePending = false;
        if (!pasteFromClipboard(false)) showKeyboard();
    }

    /** @param explicit true when the user tapped Paste (always paste text), false for auto-paste. */
    private boolean pasteFromClipboard(boolean explicit) {
        String text = Clip.read(this);
        if (text == null || text.trim().isEmpty()) {
            if (explicit) showStatus(getString(R.string.clipboard_empty));
            return false;
        }
        if (!explicit) {
            // Auto-paste only real numbers, and not the one we just copied — the user wants a new one.
            String last = store.getLast();
            boolean own = last != null && PhoneCleaner.clean(text, home).e164().equals(last);
            if (!Clip.looksLikeNumber(text) || own) return false;
        }
        setInput(text.trim());
        return true;
    }

    // ---- Cleaning ----

    private void onFormatChanged() {
        updatePlusEnabled();
        store.setFormat(includeCode.isChecked(), includePlus.isChecked());
        refresh();
        renderHistory();
    }

    private void updatePlusEnabled() {
        // "+" only makes sense in front of a country code.
        includePlus.setEnabled(includeCode.isChecked());
        includePlus.setAlpha(includeCode.isChecked() ? 1f : 0.45f);
    }

    private String formatted() {
        return current.format(includeCode.isChecked(), includePlus.isChecked());
    }

    private void refresh() {
        String raw = input.getText().toString();
        current = PhoneCleaner.clean(raw, home, forced);
        clearButton.setVisibility(raw.isEmpty() ? View.GONE : View.VISIBLE);

        boolean empty = current.isEmpty();
        String out = formatted();
        output.setText(empty ? getString(R.string.output_placeholder) : out);
        output.setTextColor(getColor(empty ? R.color.text_hint : R.color.text_primary));
        output.setContentDescription(empty ? null : getString(R.string.output_cd, spaced(out)));
        copyButton.setEnabled(!empty);
        shareButton.setEnabled(!empty);
        shareButton.setAlpha(empty ? 0.38f : 1f);
        boolean canSend = store.sendBlockReason(current) == 0;
        sendButton.setEnabled(canSend);
        sendButton.setAlpha(canSend ? 1f : 0.38f);

        // Country line: "🇨🇲 Cameroon · Mobile · from the number ▾"
        Country c = current.country;
        if (c != null) {
            StringBuilder sb = new StringBuilder(c.flag()).append("  ").append(c.name);
            if (current.network != null) sb.append(" · ").append(getString(R.string.likely_network, current.network));
            if (current.lineType != null) sb.append(" · ").append(current.lineType);
            if (current.detected && c != home) sb.append(" · ").append(getString(R.string.meta_detected));
            else if (forced != null) sb.append(" · ").append(getString(R.string.meta_chosen));
            sb.append("  ▾");
            meta.setText(sb);
            meta.setContentDescription(getString(R.string.meta_cd, sb.toString().replace("▾", "")));
            meta.setVisibility(View.VISIBLE);
        } else {
            meta.setVisibility(View.GONE);
        }

        status.setTextColor(getColor(R.color.text_secondary));
        status.setText(raw.trim().isEmpty() ? getString(R.string.status_hint) : describe(out, raw.trim()));
        setOptional(fixView, current.fix == null ? null : "✎  " + current.fix);
        String warning = current.warning;
        if (warning == null && !includeCode.isChecked() && current.kind == PhoneCleaner.Kind.UNKNOWN_CODE) {
            warning = getString(R.string.warn_cant_drop_code);
        }
        setOptional(warningView, warning == null ? null : "⚠  " + warning);
    }

    private String describe(String out, String raw) {
        if (current.kind == PhoneCleaner.Kind.EMPTY) return getString(R.string.status_no_digits);
        if (current.kind == PhoneCleaner.Kind.SHORT_CODE) return getString(R.string.status_short_code);
        if (out.equals(raw)) return getString(R.string.status_already_clean);
        String cc = current.country != null ? current.country.dialCode : "";
        boolean withCode = includeCode.isChecked();
        switch (current.kind) {
            case INTERNATIONAL:
                return getString(withCode ? R.string.status_cleaned : R.string.status_code_removed, cc);
            case EXIT_CONVERTED:
                return getString(withCode ? R.string.status_exit_converted : R.string.status_code_removed, cc);
            case HAD_CODE:
                return getString(withCode ? R.string.status_had_code : R.string.status_code_removed, cc);
            case DETECTED:
                return getString(R.string.status_detected, cc);
            case UNKNOWN_CODE:
                return getString(R.string.status_cleaned, cc);
            case LOCAL:
            default:
                return getString(withCode ? R.string.status_added_code : R.string.status_local, cc);
        }
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setTextColor(getColor(R.color.text_secondary));
    }

    private static void setOptional(TextView v, String text) {
        v.setText(text);
        v.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void copyCurrent() {
        if (current == null || current.isEmpty()) return;
        Clip.copy(this, formatted());
        store.remember(current.e164());
        flashTopRow = true;
        renderHistory();
        copyButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        copyButton.setText(R.string.copied);
        handler.removeCallbacks(resetCopyLabel);
        handler.postDelayed(resetCopyLabel, 1500);
    }

    private void shareCurrent() {
        if (current == null || current.isEmpty()) return;
        store.remember(current.e164());
        flashTopRow = true;
        renderHistory();
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, formatted());
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    /** Opens the send-money dialog for the number on screen. */
    private void sendCurrent() {
        if (current == null || current.isEmpty()) return;
        hideKeyboard();
        startActivity(UssdActivity.intent(this, current.e164()));
    }

    // ---- Settings ----

    private void setupSettings() {
        for (CheckBox c : new CheckBox[]{autoCopySetting, directDialSetting}) c.setSaveEnabled(false);
        autoCopySetting.setChecked(store.autoCopyOnPaste());
        autoCopySetting.setOnCheckedChangeListener((b, on) -> store.setAutoCopyOnPaste(on));

        ussdSetting.setSaveEnabled(false);
        ussdSetting.setText(store.ussdTemplate());
        ussdSetting.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                boolean ok = Ussd.isValidTemplate(s.toString().trim());
                ussdHelp.setText(ok ? R.string.setting_ussd_help : R.string.setting_ussd_invalid);
                ussdHelp.setTextColor(getColor(ok ? R.color.text_secondary : R.color.warning));
            }
        });
        // Saved only when editing is finished, never half-typed.
        ussdSetting.setOnFocusChangeListener((v, focused) -> { if (!focused) commitUssdTemplate(); });
        ussdSetting.setOnEditorActionListener((v, actionId, event) -> {
            commitUssdTemplate();
            hideKeyboard(ussdSetting);
            return true;
        });
        findViewById(R.id.setting_ussd_reset).setOnClickListener(v -> {
            ussdSetting.setText(Ussd.DEFAULT_TEMPLATE);
            commitUssdTemplate();
        });

        syncDirectDial();
    }

    private final android.widget.CompoundButton.OnCheckedChangeListener directDialListener = (b, on) -> {
        if (on && !hasCallPermission()) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PHONE);
        } else {
            store.setDirectDial(on);
        }
    };

    /** Saves the template if valid; otherwise puts the saved one back. */
    private void commitUssdTemplate() {
        String t = ussdSetting.getText().toString().trim();
        if (Ussd.isValidTemplate(t)) {
            store.setUssdTemplate(t);
        } else {
            ussdSetting.setText(store.ussdTemplate());
            Toast.makeText(this, R.string.setting_ussd_reverted, Toast.LENGTH_LONG).show();
        }
    }

    /** Keeps the checkbox and the stored setting in line with the actual permission. */
    private void syncDirectDial() {
        if (!hasCallPermission()) store.setDirectDial(false);
        directDialSetting.setOnCheckedChangeListener(null);
        directDialSetting.setChecked(store.directDial());
        directDialSetting.setOnCheckedChangeListener(directDialListener);
    }

    private boolean hasCallPermission() {
        return checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_CALL_PHONE) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        store.setDirectDial(granted);
        syncDirectDial();
        if (!granted) Toast.makeText(this, R.string.setting_direct_dial_denied, Toast.LENGTH_LONG).show();
    }

    // ---- Country picker ----

    private void renderCountryButton() {
        countryButton.setText(home.flag() + "  " + home.name + "  +" + home.dialCode);
        countryButton.setContentDescription(getString(R.string.country_button_cd, home.name, home.dialCode));
    }

    private void openCountryPicker() {
        if (dialogOpen()) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_country, null);
        EditText search = view.findViewById(R.id.search);
        ListView list = view.findViewById(R.id.list);
        list.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);

        Country selected = current != null && current.country != null && !input.getText().toString().isEmpty()
                ? current.country : home;
        CountryAdapter adapter = new CountryAdapter(selected);
        list.setAdapter(adapter);
        int pos = adapter.indexOf(selected);
        if (pos > 2) list.setSelection(pos - 2);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
                list.setSelection(0);
            }
        });

        dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.picker_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        list.setOnItemClickListener((parent, v, position, id) -> {
            pickCountry(adapter.getItem(position));
            dialog.dismiss();
        });
        dialog.show();
    }

    private void pickCountry(Country c) {
        home = c;
        store.setCountry(c);
        // Applies to the number in the field too, overriding any guess.
        forced = input.length() > 0 ? c : null;
        renderCountryButton();
        refresh();
        renderHistory();
        NumberWidget.updateAll(this);
    }

    /** Searchable country list: matches name, ISO code or dialling code ("237", "+237"). */
    private final class CountryAdapter extends BaseAdapter {
        private final Country selected;
        private final List<Country> items = new ArrayList<>(Country.all());

        CountryAdapter(Country selected) {
            this.selected = selected;
        }

        void filter(String query) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            String digits = PhoneCleaner.digitsOf(q);
            items.clear();
            for (Country c : Country.all()) {
                boolean match = q.isEmpty()
                        || (!digits.isEmpty() && c.dialCode.startsWith(digits))
                        || c.name.toLowerCase(Locale.ROOT).contains(q)
                        || c.id.equalsIgnoreCase(q);
                if (match) items.add(c);
            }
            notifyDataSetChanged();
        }

        int indexOf(Country c) {
            return items.indexOf(c);
        }

        @Override public int getCount() { return items.size(); }
        @Override public Country getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_country, parent, false);
            Country c = items.get(position);
            ((TextView) row.findViewById(R.id.country_name)).setText(c.flag() + "   " + c.name);
            ((TextView) row.findViewById(R.id.country_code)).setText("+" + c.dialCode);
            row.findViewById(R.id.country_check).setVisibility(c == selected ? View.VISIBLE : View.INVISIBLE);
            return row;
        }
    }

    // ---- History ----

    private void renderHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        List<Store.Entry> entries = store.getHistory();
        historyEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        historyList.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        clearHistoryButton.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);

        boolean withCode = includeCode.isChecked();
        boolean withPlus = includePlus.isChecked();
        LayoutInflater inflater = LayoutInflater.from(this);
        long now = System.currentTimeMillis();
        for (int i = 0; i < entries.size(); i++) {
            Store.Entry e = entries.get(i);
            PhoneCleaner.Result r = PhoneCleaner.clean(e.number, home);
            String shown = r.format(withCode, withPlus);

            View row = inflater.inflate(R.layout.item_history, historyList, false);
            TextView number = row.findViewById(R.id.history_number);
            TextView detail = row.findViewById(R.id.history_time);
            ImageButton copy = row.findViewById(R.id.history_copy);

            number.setText(shown);
            CharSequence when = DateUtils.getRelativeTimeSpanString(e.time, now, DateUtils.MINUTE_IN_MILLIS);
            String where = r.country == null ? ""
                    : r.country.flag() + " " + (r.network != null ? r.network : r.country.name) + " · ";
            detail.setText(where + when);
            copy.setContentDescription(getString(R.string.copy_number_cd, shown));
            copy.setOnClickListener(v -> {
                Clip.copy(this, shown);
                store.remember(e.number);
                flashTopRow = true;
                renderHistory();
            });
            row.setOnClickListener(v -> setInput(e.number));
            row.setOnLongClickListener(v -> {
                if (dialogOpen()) return true;
                dialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.remove_one, shown))
                        .setPositiveButton(R.string.remove, (d, w) -> {
                            store.remove(e.number);
                            renderHistory();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
            if (i == 0 && flashTopRow) {
                row.setAlpha(0.2f);
                row.animate().alpha(1f).setDuration(450).start();
            }
            historyList.addView(row);
        }
        flashTopRow = false;
    }

    private void confirmClearHistory() {
        if (dialogOpen()) return;
        dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear, (d, w) -> {
                    store.clearHistory();
                    renderHistory();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- Helpers ----

    /** "+237677" → "+ 2 3 7 6 7 7" so TalkBack reads digits rather than a huge number. */
    private static String spaced(String number) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(number.charAt(i));
        }
        return sb.toString();
    }

    private void showKeyboard() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        // Small delay: some keyboards ignore requests made in the same frame the window gains focus.
        if (imm != null) input.postDelayed(() -> imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT), 120);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        // Hand focus to the parent (not the first field on the page, which would scroll to the top).
        if (v.getParent() instanceof View) {
            View parent = (View) v.getParent();
            parent.setFocusableInTouchMode(true);
            parent.requestFocus();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }
}
