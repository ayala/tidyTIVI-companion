# tidyTIVI companion 0.7.0-rc.6

A small Android TV / Fire TV receiver for bundles exported by the
[tidyTIVI Dispatcharr plugin](https://github.com/ayala/tidyTIVI).

## Preservation preview: 0.7.0-rc.6

[Download the preview APK](https://github.com/ayala/tidyTIVI-companion/releases/download/v0.7.0-rc.6/tidyTIVI-companion.apk).
The latest-release link still installs stable 0.6.1, which replaces receiver data.

Press **Update TiviMate** and choose each time:

- **Keep my settings:** choose **Open TiviMate**, then **Settings → General →
  Back up data → Internal shared storage → Save**. Do not choose a subfolder.
  Return to the companion immediately after saving. It detects the new backup,
  downloads the export and merges it while retaining personal state. Confirm
  TiviMate's native **Restore** prompt when ready.
- **Replace everything:** confirm the overwrite, download the export and confirm
  TiviMate's **Restore** prompt. No local backup is required.

The companion records existing backup filenames and the time Keep was selected.
Only a new backup created after that point is eligible; old backups are never
silently selected. Neither mode is remembered as the default. Any changes made
in TiviMate after creating the backup cannot be included in the merge.

This flow needs no Accessibility permission, root, ADB or computer during use.
The previous accessibility approach failed on a physical AFTMM Firestick running
Fire OS 6.7.1.1; it is not used by this flow. The companion opens TiviMate itself,
not its private backup menu. Existing tidyTIVI playlists are matched in place;
new exported profiles can be added without recreating existing ones. Unrelated
or ambiguous playlist/provider mappings stop the merge.

Both live TV and VOD use the exported account. Matching native provider IDs keep
receiver favorites and watch progress. Unavailable titles keep stored history
but TiviMate may hide them. Different providers' IDs are not interchangeable.
The tested backup format is TiviMate 5.3.3, schema 60.

### Provider master playlists

Plugin 0.5.12 can include disabled, unfiltered XC live master playlists alongside
curated profiles. rc.5 adds these during Keep mode, avoids duplicates on repeat
merges, and preserves the receiver's enabled state and channel favorites when
export credentials change. Enable a master in TiviMate Settings → Playlists.
M3U/FAST fallback channels are regular curated channels and retain their direct
URLs; recipient XC credentials only affect XC streams.

Master creation, repeat merges with different database IDs, enabled-state and
favorite preservation across account changes passed Android SQLite regression
tests. Physical Firestick/native playlist-refresh verification is not yet done
for this feature.

### Storage use

rc.3 clears abandoned companion download/merge files on every launch and idle
return to the app, as well as before an update, including
leftovers from interrupted attempts. During a merge it discards decrypted ZIPs
and extracted databases as soon as each stage no longer needs them, and reuses
the downloaded bundle directory rather than copying the whole bundle again.
Large transaction files live in app-private files storage rather than Android's
reclaimable cache. Temporary cleanup is also attempted after failures. It no longer keeps a second
private copy of the user's pre-update backup.

Your manually saved TiviMate backups and active setup are not deleted. The final
restore file and installed logos remain available to TiviMate. Keep mode still
needs room for two expanded databases and SQLite's working files during the
merge; a small compressed backup does not imply small workspace requirements.
If storage is exhausted, the app gives a specific storage error.

rc.3's cleanup and progressive codec tests passed locally, and the signed APK
build passed. On the physical AFTMM Firestick, cleanup removed a 135 MB abandoned
bundle. A real Dropbox download and fresh-backup merge reached TiviMate's Restore
confirmation after moving large scratch files out of Android cache. Temporary
storage was released afterward. Restore was not confirmed during this test.
A launch-cleanup test on the Firestick removed a synthetic abandoned directory
while retaining the current bundle and saved connection. The resulting native
backup passed authentication, ZIP CRC and SQLite integrity checks.
Cleanup skips an active download or merge, so opening the app cannot delete
files that its worker is still using.

### Storage failures while TiviMate updates EPG

Cleanup cannot reclaim another app's active guide data. On the tested Firestick,
TiviMate's startup EPG update consumed roughly 1.6 GB before the companion even
started downloading; the Fire TV app-details screen showed 1.99 GB of TiviMate
data. This could overlap the merge and cause an intermittent ENOSPC failure.

For that installation, with the owner's approval, **TiviMate → Settings → EPG →
Update on app start** was disabled. The **24-hour update interval** and all EPG
sources were left unchanged. Free space then remained stable with TiviMate open.
Create a fresh backup after changing this setting so Keep mode preserves it.
The companion does not automatically change EPG settings or clear TiviMate data.
A scheduled/manual guide update still requires its own working space; avoiding
simultaneous updates does not prove that every guide will fit or load correctly.

rc.4 shows separate backup checking, staging, file verification and installation
steps, and logs the failing storage operation to aid diagnosis.

### Verification

On a physical AFTMM Firestick running Fire OS 6.7.1.1 and TiviMate 5.3.3,
rc.2 rejected the pre-existing backup, detected a newly created native backup,
downloaded the actual Dropbox export, merged it, and opened the native Restore
confirmation. Restore was deliberately not confirmed during this test.
Both preference files were byte-for-byte unchanged; SQLite integrity passed.
All 486 existing channel personal-state rows, 213,175 movie personal-state rows,
45,857 series personal-state rows, and four playlist IDs were retained. The new
UK profile was added once. Empty history tables are not evidence of watched
progress on this device.

Separate Android regression tests cover favorites, hidden settings, movie and
episode progress, exported-account migration, new profiles and repeat merges:
`python3 tests/run_android_merge.py emulator-5560`.

The companion background matches TiviMate's dark charcoal. Launcher PNG icon
and banner assets are included, but this Fire OS launcher still displayed a
blank tile after an in-place upgrade; the in-app logo renders correctly.

## Stable 0.6.1: install and update

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
the home screen. After saving, a 20-second grace period lets the browser finish or retry its
confirmation without changing the saved link. Before saving, the listener stops
on Back, leaving the app, or after 10 minutes. It does not serve backups, credentials, or previously saved links.
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

## 0.6.1 phone connection fix

Corrects the referrer policy so Safari sends the expected same-origin form origin.
Desktop Safari previously rejected the submission and now displays Connected.
Handles browser preconnections concurrently and finishes the HTTP response before
closing the setup session. Retried submissions acknowledge the existing save.
Regression tests cover idle connections, complete responses, retries, invalid
links, origin checks, and cancellation. Physical Firestick/iPhone verification
remains pending. Update the APK on the Firestick and scan a fresh Connect QR.

A saved link changes Connect to a green Connected button, including after reopening
the app. It stays focused and clickable to replace the link. This indicates that
the link was saved; Update TiviMate still verifies the downloaded bundle.

Verified 0.6.1 QR submission and retry in the Android TV emulator; the real saved
link was preserved and the home screen returned successfully.

## Connect without pasting a link

With plugin 0.5.13+, download `tidytivi-setup.json` from the plugin's **Docs** page
and send it privately to the receiver. Save it in Files on the phone. Open
**Connect** on the TV, scan its QR on the same Wi-Fi, and tap **Choose setup file**.
Selecting the file connects automatically and saves the link for future updates.
**Enter a link instead** retains the existing manual method. No additional hosted
service or recipient cloud account is needed. The setup file grants access to the
export and must remain private. Physical iPhone/Firestick file selection still
requires device verification.
