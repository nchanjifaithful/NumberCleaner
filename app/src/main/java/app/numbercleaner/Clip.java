package app.numbercleaner;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

/** Clipboard helpers. No permission is needed to write; reading only happens while the app is in front. */
public final class Clip {

    private Clip() {
    }

    public static boolean copy(Context context, String number) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || number == null || number.isEmpty()) return false;
        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_label), number));
        // Android 13+ shows its own copy confirmation; avoid a duplicate toast there.
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, context.getString(R.string.copied_toast, number), Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** Current clipboard text, or null. Call only from a focused activity. */
    public static String read(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipDescription d = cm.getPrimaryClipDescription();
        if (d != null && !d.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                && !d.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
            return null;
        }
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        CharSequence t = clip.getItemAt(0).coerceToText(context);
        return t == null ? null : t.toString();
    }

    /** True if the text looks like it contains a phone number worth auto-pasting. */
    public static boolean looksLikeNumber(String text) {
        if (text == null || text.length() > 64) return false;
        return PhoneCleaner.digitsOf(text).length() >= 6;
    }
}
