# Number Cleaner

A tiny Android app (about 80 KB, no permissions, no third-party libraries) that turns messy phone numbers into clean, copy-ready ones.

`(+237) 677-12-34-56` → `+237677123456`, `237677123456` or `677123456`, depending on the format you choose.

## Features

- **Live cleaning.** Spaces, dashes, dots, brackets, labels ("Phone:", "tel:") and extensions are removed as you type.
- **Output format.** Two checkboxes control what gets copied:
  - **Country code** off gives local format: `677123456`, or `08031234567` for Nigeria, which keeps its leading 0.
  - **Leading +** off gives `237677123456`.
- **Automatic country detection.**
  - A number with `+` or `00` is always read from its own code: 🇬🇧 for +44, 🇯🇲 for +1 876, 🇰🇿 for +7 7…, and so on.
  - Without `+`, the number is first tried as a local number of your country. If it can't be one, a long number such as `447946095800` is recognised as another country's.
  - Tap the country line under the result to pick a different country. The picker covers about 200 countries and you can search by name or code.
- **Cameroon rules** (ART numbering plan, 9 digits after +237):
  - The code is never added twice: `237 677 12 34 56` → `+237677123456`.
  - Old 8-digit numbers get the missing digit: mobiles get a **6** (`237 77 12 34 56` → `+237677123456`), fixed lines get a **2** (`22 12 34 56` → `+237222123456`). The app always shows a ✎ note when it does this.
  - Mobile (6…) and fixed-line (2…) numbers are labelled. Wrong lengths or prefixes get a ⚠ warning and are never silently changed.
  - Operators are not shown, because number portability means a prefix doesn't prove the current network.
- **General fixes.** Trunk 0s are dropped (`0803…` → `+234803…`). An extra 0 after the code is removed (`+234 0803…`). `00`/`011` become `+`. Short codes like `112` are left alone.
- **Recents.** Every copy or share goes to the top of Recent, which keeps 15 numbers. They're shown in your current format: tap a row to reload it, use the icon to copy it, long-press to remove it.
- **Widget.** Shows the last copied number with a copy button and a ✨ button. ✨ cleans whatever number is on your clipboard and copies it back without opening the app. Make the widget taller to see up to 3 more recent numbers, each tap-to-copy.
- **Launcher shortcut.** Long-press the app icon and choose **Clean clipboard** to do the same as ✨.
- **Share and text selection.** You can share a number into the app, or select a number in any app and choose **Clean number**.
- **Design.** Light and dark mode, with Material You colours on Android 12+.

## Install

Copy `NumberCleaner.apk` to the phone and open it. It needs Android 7.0 or newer. Version 1.1 installs over 1.0 and keeps your recents.

## Build from source

Open the folder in Android Studio, or run `./gradlew assembleDebug`. Unit tests for all cleaning rules run with `./gradlew test`.

## Code map

| File | What it does |
|---|---|
| `PhoneCleaner.java` | Cleaning, detection, Cameroon rules and output formats. Pure Java, unit-tested. |
| `Country.java` | Calling codes, number lengths and trunk/exit prefixes for about 200 countries |
| `MainActivity.java` | The single screen |
| `QuickCleanActivity.java` | Invisible one-tap clipboard cleaner used by the widget and shortcut |
| `NumberWidget.java` | Home-screen widget |
| `Store.java` / `Clip.java` | Settings, history and clipboard |
