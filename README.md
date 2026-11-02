# Number Cleaner

A tiny Android app that turns messy phone numbers into clean, copy-ready ones.

| Input | Output format | Result |
|---|---|---|
| `(+237) 677-12-34-56` | Country code and `+` | `+237677123456` |
| `(+237) 677-12-34-56` | Country code only | `237677123456` |
| `(+237) 677-12-34-56` | Local | `677123456` |

Number Cleaner is about 105 KB, works offline and uses no third-party runtime libraries. It can also start a mobile money transfer to a cleaned number with a USSD code.

## Permissions

The app only uses the phone permission (`CALL_PHONE`), and only if you turn on **Dial directly** in Settings. Android asks for it at that moment, not at install. Everything else works without it.

## Features

### Cleaning

- Removes spaces, dashes, dots, brackets, labels such as `Phone:` or `tel:`, and extensions as you type.
- Lets you choose the output format with two options: **Country code** and **Leading +**.
- Drops trunk zeros (`0803…` → `+234803…`) and an extra zero after the code (`+234 0803…`).
- Converts the `00` and `011` international prefixes to `+`.
- Leaves short codes such as `112` unchanged.

### Country detection

- Reads numbers that start with `+` or `00` from their own country code.
- Tries other numbers as local numbers for your selected country first, then checks whether a long number such as `447946095800` belongs to another country.
- Includes a picker with about 200 countries, searchable by name or code.

### Cameroon numbering rules

Numbers follow the ART numbering plan, with 9 digits after `+237`.

- The country code is never added twice: `237 677 12 34 56` → `+237677123456`.
- Old 8-digit numbers get their missing digit, and the app always shows a note when this happens:
  - Mobile numbers get a `6`: `237 77 12 34 56` → `+237677123456`.
  - Fixed lines get a `2`: `22 12 34 56` → `+237222123456`.
- Numbers are labelled as mobile or fixed line.
- Wrong lengths or prefixes show a warning and are never silently changed.
- The likely network is shown from the prefix:

| Network | Prefixes |
|---|---|
| MTN | `67`, `68`, `650`–`654` |
| Orange | `69`, `655`–`659` |
| Nexttel | `66` |
| Camtel | `62` (mobile), `2` (fixed line) |

Networks are labelled "Likely" because ported numbers keep their original prefix.

### Send money

- **In the app:** **Send** opens a small dialog with the recipient's local number, their likely network, the amount and the exact code to dial.
- **On the widget:** on the larger widget sizes, **Send** dials straight away with your default amount, and the button shows it, such as "Send 5k". With no default amount set, it asks. The small widget always asks, and you can make every size ask in Settings.
- **USSD code:** the default is `*126*9*{number}*{amount}#` (MTN MoMo). If your operator uses a different code, change it in Settings.
- **Dialing:** the app opens your phone's dialer with the code filled in. **Dial directly**, an optional setting, calls the code straight away.
- **Confirming:** your phone then shows the operator's menu, where you confirm and enter your PIN. The app never sees your PIN.
- **Safeguards:** numbers from another country, unknown numbers and numbers with a warning can't be sent to. Repeat taps on the widget's instant Send are ignored for a few seconds.

### Home-screen widget

The widget works without opening the app, and its layout changes as you resize it:

| Widget size | What it shows |
|---|---|
| Small (1 row) | The number, with Paste, Copy and Send icons |
| Medium (4×2) | Adds the country and network line, country code and `+` toggles, and labelled buttons |
| Tall | Adds up to 3 recent numbers, each copied with a tap |

When the widget is empty, it shows a single wide Paste button. Turn on automatic copy after Paste in Settings to clean and copy with one tap.

### More ways to use it

- **Navigation:** a bottom bar switches between the **Clean**, **Recent** and **Settings** tabs.
- **Recent numbers:** keeps the last 15 numbers you copied or shared.
- **Launcher shortcut:** long-press the app icon and choose **Clean clipboard** to clean and copy in one step.
- **Sharing:** share a number into the app, or select a number in any app and choose **Clean number**.
- **Design:** supports light and dark mode, with Material You colours on Android 12 and newer.

## Install

1. Download `NumberCleaner-v1.4.apk` from the [latest release](../../releases/latest).
2. Open the file on your phone and allow installs from that app if Android asks.

The app needs Android 7.0 (API 24) or newer. APKs for earlier versions are on the [releases page](../../releases).

## Build from source

### Requirements

- JDK 17 or newer
- Android SDK for API 34

Android Studio can provide the JDK and SDK. The included Gradle wrapper downloads the correct Gradle version.

### Build

Open the project in Android Studio and press **Run**, or build from the command line:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On Windows, use `gradlew.bat` instead of `./gradlew`.

### Test

The cleaning, detection, output and USSD rules have unit tests in `app/src/test`:

```bash
./gradlew test
```

### Add the widget

Long-press the home screen, choose **Widgets → Number Cleaner**, and drag it into place.

## Project structure

All source files are in `app/src/main/java/app/numbercleaner/`.

| File | Purpose |
|---|---|
| `PhoneCleaner.java` | Cleaning, detection, Cameroon rules and output formats, as plain Java |
| `Country.java` | Calling codes, number lengths, and trunk and exit prefixes |
| `MainActivity.java` | The app screen, with Clean, Recent and Settings tabs |
| `QuickCleanActivity.java` | Invisible screen behind the widget's Paste and Send buttons and the launcher shortcut |
| `UssdActivity.java` | Send-money dialog |
| `Ussd.java` | USSD code templates, as plain Java |
| `NumberWidget.java` | Home-screen widget |
| `Store.java` | Settings and history |
| `Clip.java` | Clipboard helpers |

The Cameroon network prefixes are in the `CM_NETWORKS` table in `PhoneCleaner.java`. Update that table if the ART plan changes.

## Release signing

The release build is signed with the debug key so that `assembleRelease` produces an installable APK. Add your own `signingConfig` in `app/build.gradle.kts` before publishing to a store.

## Version history

| Version | Highlights |
|---|---|
| [`v1.4`](../../releases/tag/v1.4) | Three-tab navigation, a default amount, and instant Send from the widget |
| [`v1.3`](../../releases/tag/v1.3) | Send money by USSD, new Settings, and a redesigned widget with a Send button |
| [`v1.2`](../../releases/tag/v1.2) | Likely-network labels for Cameroon and a widget layout that adapts to its size |
| [`v1.1`](../../releases/tag/v1.1) | Output format options, detection across about 200 countries, Cameroon rules and one-tap clipboard cleaning |
| [`v1.0`](../../releases/tag/v1.0) | First release with live cleaning, default country code, copy and share, recent numbers, and a widget |
