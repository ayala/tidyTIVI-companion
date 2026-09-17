# tidyTIVI companion 0.4.0

A small Android TV / Fire TV receiver for bundles exported by the
[tidyTIVI Dispatcharr plugin](https://github.com/ayala/tidyTIVI).

## Install and update

1. Download [the APK](https://github.com/ayala/tidyTIVI-companion/releases/latest/download/tidyTIVI-companion.apk) and sideload it onto an Android-based Fire TV or Android TV device.
2. Install and activate TiviMate separately. This release was tested with TiviMate 5.3.3.
3. Open tidyTIVI, allow file access, and save the private bundle download link from the plugin. HTTPS links and Dropbox shared links are supported.
4. Press **Update TiviMate**. The app downloads and verifies the bundle, installs the backup, playlists and complete logo folders, then opens TiviMate's native Restore prompt.
5. Confirm **Restore**. Restoring replaces the existing TiviMate setup; include all desired profiles in one export. TiviMate also needs access to the shared logo directory.

Repeat Update TiviMate after publishing a new bundle to the same cloud link.
No root, emulator worker, background service or Git clone is needed on the receiver.
Files live under `/sdcard/Download/tidyTIVI/current`. Provider credentials and the
private download link are never embedded in the published APK. Keep your actual
bundle and link private: they grant access to the exported accounts.

The app verifies every file against the bundle manifest's SHA-256 and size,
rejects unexpected files and unsafe ZIP paths, and retains the previous bundle
if verification fails. Checksums detect corruption; use a trusted HTTPS link.
A unique content URI hands each backup to TiviMate for user-confirmed restoration.

## Build

Requires Python 3, a JDK with Java 8 compilation support, and Android SDK build
tools 36.0.0 plus platform android-37.0. The APK supports Android API 23+.
Set `ANDROID_SDK_ROOT` and `JAVA_HOME` to your installed SDK and JDK, then run:

```sh
python3 build.py
```

By default a development keystore is created outside this repository under
`~/.local/share/tidytivi/`. For a signed release with an existing key, set
`TIDYTIVI_SIGNING_KEY`, `TIDYTIVI_KEY_ALIAS`, `TIDYTIVI_STORE_PASSWORD`, and
`TIDYTIVI_KEY_PASSWORD`. Retain your key to install future updates over your own
builds. Independent builds cannot replace the published APK unless signed with
the same key. Override SDK versions using `ANDROID_BUILD_TOOLS` and
`ANDROID_PLATFORM` when needed. Output: `build/tidyTIVI-companion.apk`.

## Verification and limits

Tested on an unrooted Android TV emulator with TiviMate 5.3.3: bundle download,
logo installation, native Restore confirmation, repeated restore, and rejection
of corrupted downloads while retaining the previous backup. Physical Fire TV
hardware and an actual Dropbox-hosted download still need testing. First-run
file permission screens vary by Android/Fire OS version. This app neither installs
nor activates TiviMate and cannot silently confirm its Restore dialog.
