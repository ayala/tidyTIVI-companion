# tidyTIVI companion 0.6.0

A small Android TV / Fire TV receiver for bundles exported by the
[tidyTIVI Dispatcharr plugin](https://github.com/ayala/tidyTIVI).

## Install and update

1. Download [the APK](https://github.com/ayala/tidyTIVI-companion/releases/latest/download/tidyTIVI-companion.apk) and sideload it onto an Android-based Fire TV or Android TV device.
2. Install and activate TiviMate separately. This release was tested with TiviMate 5.3.3.
3. Open tidyTIVI and choose **Connect**. Scan the QR with a phone on the same Wi-Fi, paste your plugin’s Dropbox bundle link into the phone page, and tap **Connect**. Alternatively, choose **Enter link manually** on the TV. [Plugin connection guide](https://github.com/ayala/tidyTIVI/blob/main/CLOUD-SETUP.md#plugin-users). Existing saved HTTPS and Google Drive links remain compatible.
4. Press **Update TiviMate** and allow file access if prompted. The app downloads and verifies the bundle, installs the backup, playlists and complete logo folders, then opens TiviMate's native Restore prompt.
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

## Phone setup

One public APK works for everyone. Each user supplies their own bundle link; no
Dropbox login, developer registration, public Dispatcharr address, or hosted
pairing service is needed on the companion. Dispatcharr can be elsewhere.

Connect opens a temporary HTTP page on the TV device’s local network. The QR
contains a random, single-use session address, never your Dropbox link. The phone
page sends the link directly to the TV, which stores it privately and returns to
the home screen. The listener stops after connection, Back, leaving the app, or
10 minutes. It does not serve backups, credentials, or previously saved links.
Use a trusted Wi-Fi network: this local setup transfer uses HTTP, not TLS. Guest
Wi-Fi/client isolation, VPN routing, or firewalls can block it; manual entry is
always available. Keep the Connect screen open while setting up.

## Third-party code

QR generation uses ZXing core 3.5.3 (Apache-2.0). Its pinned JAR and license/notice
are in `libs/`; license and notice are also packaged in the APK. The build checks
the dependency’s SHA-256 before compilation.

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
hardware and actual Google Drive hosted downloads still need testing. Dropbox-hosted
download and installation were verified on the emulator with plugin 0.5.1: all
823 payload files matched the manifest. Restore was then confirmed; TiviMate
restarted with custom channel names and logos visible and live playback working.
The current post-restore EPG refresh has not yet been verified to populate listings. First-run
file permission screens vary by Android/Fire OS version. This app neither installs
nor activates TiviMate and cannot silently confirm its Restore dialog.

Google Drive file links are normalized and supported download-confirmation forms
are followed only on Google Drive hosts for the same file. Organizational sharing
restrictions and provider quotas may still prevent downloads.

## 0.6.0 verification

The signed APK was installed over 0.5.0 on the unrooted Android TV emulator,
preserving its saved link. Verified the blue buttons, rounded original icon,
full-screen QR, manual link save, and the phone-form POST saving the real Dropbox
link. The on-screen QR was decoded from a screenshot; access to the emulator’s
local page used an ADB port forward for testing only. Production Firesticks need
no ADB or tunnel. A physical phone-to-Firestick Wi-Fi test remains outstanding.

`tests/PairingServerTest.java` covers page delivery, wrong session tokens, invalid
links, cross-origin rejection, successful save, single-use closure, and cancellation.

Final 0.6.0 emulator check: Connect is first and focused on launch; Update TiviMate
is second. After saving through both QR setup and manual entry, the Dropbox bundle
downloaded successfully, all 823 payload files matched their manifest, and the
native TiviMate Restore confirmation appeared. Restore was left for the user.
