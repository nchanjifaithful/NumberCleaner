# Number Cleaner

A tiny Android app (about 60 KB, no permissions, no third-party libraries) that turns messy phone numbers into clean, copy-ready ones.

`(+237) 677-12-34-56` → `+237677123456`

## Features

- **Live cleaning.** Spaces, dashes, dots, brackets, labels ("Phone:", "tel:") and extensions ("ext. 22", "x22") are removed as you type.
- **Default country code.** Choose one of about 50 countries, enter a custom code, or pick "No country code". The first-run default comes from the SIM, then the network, then the device locale, then Cameroon.
  - Local numbers get the code. The trunk prefix is dropped where the country uses one: `0803…` → `+234803…`, `8 916…` → `+7916…`. Italy keeps its leading 0.
  - Numbers that already have the code typed without "+" are not doubled: `237 677 12 34 56` → `+237677123456`.
  - `00…` / `011…` international prefixes become `+`.
- **Smart formatting.** A leading `+` is always kept and the number's own country code is never touched. A stray `(0)` in `+44 (0)20…` is removed. Short codes like `112` are left alone. A status line says what changed, or "Already clean ✓".
- **One-tap copy** with the big Copy button, or tap the number itself. There is also a Share button.
- **Recent history.** The last 15 numbers you copied or shared. Tap a row to reload it, use the copy icon to copy it, long-press to remove one, or use Clear to remove them all.
- **Home-screen widget.** Shows the last cleaned number, with a copy button and a ✎ shortcut. The shortcut opens the app with an empty field and pastes the clipboard if it holds a new number.
- **Extras.** Share a number into the app, or select a number in any app and choose **Clean number** from the text-selection menu.
- **Design.** Light and dark mode, with Material You wallpaper colours on Android 12+. Supports TalkBack: the number is read digit by digit.

## Install the prebuilt APK

Copy `NumberCleaner.apk` to your phone, open it, and allow "Install unknown apps" for your file manager when Android asks. It needs Android 7.0 (API 24) or newer.

To add the widget, long-press the home screen, choose **Widgets → Number Cleaner**, and drag it out. It can be resized.

## Build from source

Open this folder in Android Studio and press Run. From the command line:

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # unit tests for the cleaning rules
```

The release build type signs with the debug key for convenience. Add your own `signingConfig` before publishing.

## Code map

| File | What it does |
|---|---|
| `PhoneCleaner.java` | All cleaning rules, as pure Java with unit tests in `app/src/test` |
| `Country.java` | Calling codes, national number lengths and trunk/exit prefixes |
| `MainActivity.java` | The single screen |
| `Store.java` | Saved country and history (SharedPreferences) |
| `NumberWidget.java` | Home-screen widget |
| `Clip.java` | Clipboard helpers |

To add a country, append one line to the list in `Country.java`: ISO code, name, dial code, min and max national digits, and trunk prefix.
