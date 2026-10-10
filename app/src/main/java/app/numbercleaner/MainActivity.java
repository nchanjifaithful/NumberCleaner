package app.numbercleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    /** Sent by the widget's "clean new" button: clear the field and offer the clipboard. */
    static final String ACTION_CLEAN_NEW = "app.numbercleaner.action.CLEAN_NEW";

    private static final String STATE_AUTO_PASTE = "auto_paste";

    private Store store;
    private Country country;
    private PhoneCleaner.Result current = PhoneCleaner.clean("", Country.NONE);

    private EditText input;
    private ImageButton pasteButton;
    private ImageButton clearButton;
    private Spinner countrySpinner;
    private TextView output;
    private TextView status;
    private Button copyButton;
    private ImageButton shareButton;
    private LinearLayout historyList;
    private TextView historyEmpty;
    private Button clearHistoryButton;

    private final List<Country> spinnerItems = new ArrayList<>(); // null entry = "Custom code…"
    private int spinnerSelection = -1;
    private boolean autoPastePending;
    private AlertDialog customDialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable resetCopyLabel = () -> copyButton.setText(R.string.copy);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new Store(this);
        country = store.getCountry();

        input = findViewById(R.id.input);
        pasteButton = findViewById(R.id.paste);
        clearButton = findViewById(R.id.clear);
        countrySpinner = findViewById(R.id.country);
        output = findViewById(R.id.output);
        status = findViewById(R.id.status);
        copyButton = findViewById(R.id.copy);
        shareButton = findViewById(R.id.share);
        historyList = findViewById(R.id.history_list);
        historyEmpty = findViewById(R.id.history_empty);
        clearHistoryButton = findViewById(R.id.clear_history);

        // Phone keypad, but keep the plain text key listener so pasted text isn't filtered char-by-char
        // (that would glue extension digits onto the number).
        input.setRawInputType(InputType.TYPE_CLASS_PHONE);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
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
        copyButton.setOnClickListener(v -> copyCurrent());
        shareButton.setOnClickListener(v -> shareCurrent());
        clearHistoryButton.setOnClickListener(v -> confirmClearHistory());
        output.setOnClickListener(v -> copyCurrent());

        setupCountrySpinner();
        renderHistory();

        if (savedInstanceState != null) {
            autoPastePending = savedInstanceState.getBoolean(STATE_AUTO_PASTE);
        } else if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            // Reopened from Recents: don't replay an old share / "clean new" request.
            handleIntent(getIntent());
        }
        refresh();
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Android 10+ only lets the focused app read the clipboard, so wait for focus.
        if (hasFocus) runAutoPaste();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Leaving with a good result counts as "cleaned", so the widget shows it.
        if (isRememberable()) store.remember(current.number);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderHistory();
    }

    @Override
    protected void onDestroy() {
        if (customDialog != null && customDialog.isShowing()) customDialog.dismiss();
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
            input.setText("");
            autoPastePending = true;
        }
        if (text != null) {
            input.setText(text);
            input.setSelection(input.length());
        }
    }

    private void runAutoPaste() {
        if (!autoPastePending) return;
        autoPastePending = false;
        if (!pasteFromClipboard(false)) showKeyboard();
    }

    /** @param explicit true when the user tapped Paste (always paste text), false for auto-paste. */
    private boolean pasteFromClipboard(boolean explicit) {
        String text = Clip.read(this);
        // Auto-paste skips the number we copied ourselves — the user wants a *new* one.
        boolean ownNumber = text != null && text.trim().equals(store.getLast());
        if (text == null || (!explicit && (!Clip.looksLikeNumber(text) || ownNumber))) {
            if (explicit) {
                status.setText(R.string.clipboard_empty);
                status.setTextColor(getColor(R.color.text_secondary));
            }
            return false;
        }
        input.setText(text.trim());
        input.setSelection(input.length());
        return true;
    }

    // ---- Cleaning ----

    private void refresh() {
        String raw = input.getText().toString();
        current = PhoneCleaner.clean(raw, country);
        clearButton.setVisibility(raw.isEmpty() ? View.GONE : View.VISIBLE);

        boolean empty = current.isEmpty();
        output.setText(empty ? getString(R.string.output_placeholder) : current.number);
        output.setTextColor(getColor(empty ? R.color.text_hint : R.color.text_primary));
        output.setContentDescription(empty ? null : getString(R.string.output_cd, spaced(current.number)));
        copyButton.setEnabled(!empty);
        shareButton.setEnabled(!empty);
        shareButton.setAlpha(empty ? 0.38f : 1f);

        if (raw.trim().isEmpty()) {
            status.setText(R.string.status_hint);
        } else if (current.warning != null) {
            status.setText("⚠ " + current.warning);
        } else {
            status.setText(describe(current));
        }
        status.setTextColor(getColor(current.warning != null ? R.color.warning : R.color.text_secondary));
    }

    private String describe(PhoneCleaner.Result r) {
        switch (r.kind) {
            case EMPTY:
                return getString(R.string.status_no_digits);
            case INTERNATIONAL:
                return getString(r.unchanged ? R.string.status_already_clean : R.string.status_international);
            case EXIT_CONVERTED:
                return getString(R.string.status_exit_converted);
            case HAD_CODE:
                return getString(R.string.status_had_code, country.dialCode);
            case ADDED_CODE:
                return getString(R.string.status_added_code, country.dialCode);
            case DIGITS_ONLY:
                return getString(R.string.status_digits_only);
            case SHORT_CODE:
            default:
                return getString(R.string.status_short_code);
        }
    }

    private boolean isRememberable() {
        return current != null && !current.isEmpty() && current.warning == null
                && current.kind != PhoneCleaner.Kind.SHORT_CODE;
    }

    private void copyCurrent() {
        if (current.isEmpty()) return;
        Clip.copy(this, current.number);
        store.remember(current.number);
        renderHistory();
        copyButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        copyButton.setText(R.string.copied);
        handler.removeCallbacks(resetCopyLabel);
        handler.postDelayed(resetCopyLabel, 1500);
    }

    private void shareCurrent() {
        if (current.isEmpty()) return;
        store.remember(current.number);
        renderHistory();
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, current.number);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    // ---- Country selector ----

    private void setupCountrySpinner() {
        // The selection is derived from the stored country; don't let state restore re-select "Custom code…".
        countrySpinner.setSaveEnabled(false);
        rebuildSpinnerItems();
        countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == spinnerSelection) return;
                Country picked = spinnerItems.get(position);
                if (picked == null) {
                    askCustomCode();
                } else {
                    selectCountry(picked);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void rebuildSpinnerItems() {
        spinnerItems.clear();
        spinnerItems.add(Country.NONE);
        spinnerItems.addAll(Country.all());
        if (country.isCustom()) spinnerItems.add(country);
        spinnerItems.add(null);

        List<String> labels = new ArrayList<>();
        for (Country c : spinnerItems) labels.add(c == null ? getString(R.string.custom_code) : c.label());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, labels);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);

        int index = 0;
        for (int i = 0; i < spinnerItems.size(); i++) {
            Country c = spinnerItems.get(i);
            if (c != null && c.id.equals(country.id)) index = i;
        }
        spinnerSelection = index;
        countrySpinner.setSelection(index, false);
    }

    private void selectCountry(Country c) {
        country = c;
        store.setCountry(c);
        rebuildSpinnerItems();
        refresh();
    }

    private void askCustomCode() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_custom_code, null);
        EditText field = view.findViewById(R.id.code);
        if (country.isCustom()) field.setText(country.dialCode);

        final boolean[] accepted = {false};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.custom_code_title)
                .setView(view)
                .setPositiveButton(R.string.use_code, null)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {})
                .create();
        dialog.setOnDismissListener(d -> {
            // Cancelled: put the selector back on the previous choice.
            if (!accepted[0] && spinnerSelection >= 0) countrySpinner.setSelection(spinnerSelection, false);
        });
        dialog.setOnShowListener(d -> {
            field.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                String digits = PhoneCleaner.digitsOf(field.getText().toString());
                if (!Country.isValidCode(digits)) {
                    field.setError(getString(R.string.custom_code_error));
                    return;
                }
                accepted[0] = true;
                dialog.dismiss();
                Country known = null;
                for (Country c : Country.all()) {
                    if (c.dialCode.equals(digits)) { known = c; break; }
                }
                selectCountry(known != null ? known : Country.custom(digits));
            });
        });
        customDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    // ---- History ----

    private void renderHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        List<Store.Entry> entries = store.getHistory();
        historyEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        clearHistoryButton.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        long now = System.currentTimeMillis();
        for (Store.Entry e : entries) {
            View row = inflater.inflate(R.layout.item_history, historyList, false);
            TextView number = row.findViewById(R.id.history_number);
            TextView time = row.findViewById(R.id.history_time);
            ImageButton copy = row.findViewById(R.id.history_copy);

            number.setText(e.number);
            time.setText(DateUtils.getRelativeTimeSpanString(e.time, now, DateUtils.MINUTE_IN_MILLIS));
            copy.setContentDescription(getString(R.string.copy_number_cd, e.number));
            copy.setOnClickListener(v -> {
                Clip.copy(this, e.number);
                store.remember(e.number);
                renderHistory();
            });
            row.setOnClickListener(v -> {
                input.setText(e.number);
                input.setSelection(input.length());
            });
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.remove_one, e.number))
                        .setPositiveButton(R.string.remove, (d, w) -> {
                            store.remove(e.number);
                            renderHistory();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
            historyList.addView(row);
        }
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }
}
