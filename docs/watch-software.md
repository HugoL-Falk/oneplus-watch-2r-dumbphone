# OnePlus Watch 2R as a pocket music player: software guide

This guide turns a **OnePlus Watch 2R** into a pocket music player that also pays by NFC. It's meant to live in a pocket, on a lanyard or in a 3D-printed case, not on a wrist. It covers:

1. Connecting to the watch over wireless ADB
2. Debloating: removing the apps a pocket player doesn't need
3. Hidden settings: stopping pocket wake-ups and the emergency call trigger
4. Stopping the watch from locking every time it's lifted (sensor restrict mode)
5. **Pocket Player**, a small sideloaded app: Spotify controls over the album cover with a living edge visualizer, start-music, a menu with the sensor switch, and an instant-lock shortcut. The same app also contains **Pocket Face**, the same screen as a watch face (5.6)
6. A checklist, and 7. findings worth knowing if you build something similar (no LDAC, no EQ, Spotify quirks, Android gotchas, how a sideloaded watch face behaves)
8. Battery measurements, and 9. what to do when headphones connect but stay silent (a watch-side Bluetooth audio fault, fixed by a full power-off)

Everything here works **without root**, and every step can be undone.

> **Tested on:** OnePlus Watch 2R (model `OPWWE234`), Wear OS on Android 14 (API 34), build `OPWWE234_11_A.192` (`AW2A.240903.001.A3.OPWWE234_11_A.192.260910`, security patch 2026-08-01), Spotify 9.1.98. Other builds may name settings differently. If a key doesn't exist on your watch, dump the settings (step 3.1) and search for it.

> **Disclaimer:** you change system settings and remove system apps at your own risk. Nothing here voids the warranty or needs an unlocked bootloader, but a wrong package removal can break features until you restore it. Read the "do not remove" list first.

---

## 0. What you need

- The watch, set up with its phone app (**OHealth**) and your Google account
- A computer on the **same Wi-Fi** as the watch, with **ADB** (Android platform-tools). On Windows: `winget install Google.PlatformTools`
- Optional: [Universal Android Debloater (UAD-ng)](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) for point-and-click debloating
- Optional, to build the app yourself: JDK 17 and the Android SDK (see section 5.4)

Throughout this guide, `<WATCH>` means your watch's `IP:port` from wireless debugging, for example `192.168.1.45:38491`.

## 1. Connect over wireless ADB

1. On the watch: **Settings → System → About**, find **Build number** and tap it 7 times (the exact path varies by build). Developer options are now on.
2. **Settings → Developer options**: turn on **ADB debugging**, then **Wireless debugging**.
3. In Wireless debugging, tap **Pair new device**. The watch shows a pairing code and an `IP:pairing-port`.
4. On the computer:
   ```
   adb pair <IP>:<pairing-port> <code>
   adb connect <IP>:<port>        # the port shown on the main Wireless debugging screen
   adb devices                    # the watch should be listed
   ```

Things to know:
- **The port changes** every time wireless debugging restarts: after a reboot, after Wi-Fi drops, and sometimes when the watch sleeps for a long time. Pairing is only needed once; afterwards just `adb connect` to the new port.
- If the connection is refused, toggle Wireless debugging off and on and use the new port.
- When you're finished, turn wireless debugging and developer options **off** again. An open debug port costs battery and is a security risk.

## 2. Debloat

### 2.1 How to remove a package

UAD-ng does this with checkboxes. By hand:

```
adb -s <WATCH> shell pm uninstall -k --user 0 <package>     # what UAD does: removes it for your user
adb -s <WATCH> shell pm disable-user --user 0 <package>     # milder: just disables it
```

Neither touches the system partition. The APK stays in the system image, so a **factory reset brings everything back**.

To undo:
```
adb -s <WATCH> shell cmd package install-existing --user 0 <package>   # after uninstall --user 0
adb -s <WATCH> shell pm enable <package>                               # after disable-user
```

To see what you removed: `pm list packages -u` (everything, including removed) minus `pm list packages` (installed). `pm list packages -d` only shows *disabled* ones, not removed ones.

Reboot after debloating (`adb -s <WATCH> reboot`) and check that **music plays, headphones connect, Wallet opens and the screen wakes**.

### 2.2 What I removed (tested: music, Bluetooth, Wallet and time all still work)

| Group | Packages |
| --- | --- |
| Google apps not needed | `com.google.android.calendar`, `com.google.android.contacts`, `com.google.android.dialer`, `com.google.android.apps.youtube.music` (only if you use Spotify), `com.google.android.marvin.talkback`, `com.google.android.tts` |
| Assistant / hotword | `com.google.android.wearable.assistant`, `com.android.hotwordenrollment.okgoogle`, `com.android.hotwordenrollment.xgoogle` |
| Ads and telemetry | `com.android.adservices.api`, `com.android.federatedcompute.services`, `com.android.ondevicepersonalization.services`, `com.nearme.statistics.rom`, `com.oplus.postmanservice`, `com.heytap.wearable.logkit`, `com.android.traceur` |
| Demo, test and factory tools | `com.google.android.apps.wearable.retailattractloop`, `com.oppo.engineermode`, `com.oppo.bttestmode`, `com.oplus.wifitest.wifirf`, `com.oppo.lfeh` |
| Wrist-only health and sport | `com.heytap.wearable.heartrate`, `.oxygen`, `.stress`, `.sleep`, `.sports`, `.relax`, `.daily`, `.altitude` |
| Other OnePlus extras | `com.heytap.wearable.cameraassistant`, `.flashlight`, `.weather`, `com.google.android.wearable.dwf.weather`, `com.oplus.wearos.watchface.album`, `com.oplus.parentalcontrolmode` |
| Phone/SMS leftovers | `com.android.mms.service`, `com.android.providers.blockednumber`, `com.android.providers.calendar` |
| Setup wizard (disabled, not removed) | `com.oplus.wearable.setupwizard` |

Some mainline modules (`adservices`, `federatedcompute`, `ondevicepersonalization`) may refuse; skip them if they do.

Still low-risk if you want to go further: `com.google.android.apps.maps`, `com.google.android.apps.messaging`, `com.heytap.wearable.compass`, `com.android.healthconnect.controller`, `com.android.health.connect.backuprestore`.

Riskier, test carefully: `com.heytap.wearable.health` and `com.google.android.wearable.healthservices` (the OHealth phone app may need them to sync), `com.heytap.wearable.clock` (alarms), `com.oplus.stdid`.

### 2.3 Do not remove

| Why | Packages |
| --- | --- |
| Payments | `com.google.android.apps.walletnfcrel`, `com.android.nfc`, `com.android.se`, `com.google.android.clockwork.nfc`, `com.android.rkpdapp` |
| Google and Wear OS core | `com.google.android.gms`, `com.google.android.gsf`, `com.google.android.gsmediator`, `com.android.vending`, `com.google.android.wearable.app`, `com.google.wear.services`, `com.google.android.clockwork.modes`, `com.google.android.wearable.protolayout.renderer`, `com.google.wear.watchface.runtime`, `com.google.android.apps.wearable.settings`, `com.google.android.apps.wearable.systemui` |
| OnePlus system | `com.oplus.wearable.sysui`, `com.google.android.wearable.overlay.home.heytap`, `com.heytap.wearable.powermanager`, `com.heytap.wearable.mculinkservice`, `com.heytap.wearable.offload` (the low-power chip), `com.heytap.accessory` (phone link), `com.oplus.wearable.media.sessions` (media controls, see the note below) |
| Connectivity and time | `com.android.bluetooth`, `com.android.networkstack*`, `com.android.phone`, `com.android.location.fused`, `com.qualcomm.timeservice` |
| Other | `com.android.providers.media*`, `com.android.providers.contacts`, `com.google.android.inputmethod.latin` (keyboard, needed for Wi-Fi passwords), and every `*_rro`, `*.resources`, `ctsshim` and `frameworkpackagestubs` package (they use no battery anyway) |

**Note on `com.oplus.wearable.media.sessions`: keep it enabled.** The watch's settings name it as the system's media controls (`settings get global wear_media_controls_package`). It does two things. It draws the small "equalizer bars + dot" pill at the bottom of every watch face while music plays. It also provides the **output switcher** (`OutputSwitcherJetpackInvocation`): the screen that Spotify's **Connect a device** button opens, with "Play from this watch", your paired headphones and "Pair new device". Disabling it (`pm disable-user --user 0 com.oplus.wearable.media.sessions`) removes the pill, and music, headphones and Pocket Player keep working, but **Connect a device then flashes and closes**, so you can't pick headphones from inside Spotify. Confirmed on 2026-11-12: with the package enabled the button opens the switcher, and that activity belongs to this package. There's no known way to hide the pill without losing the switcher. Restore it with `pm enable --user 0 com.oplus.wearable.media.sessions`.

## 3. Hidden settings

### 3.1 See what your watch has

```
adb -s <WATCH> shell "settings list global; settings list secure; settings list system" > settings.txt
```

Keep this file private: it contains your Bluetooth MAC and device IDs.

Set a value with `settings put <namespace> <key> <value>`. To undo, put the old value back, or `settings delete <namespace> <key>` for a key that didn't exist before.

### 3.2 What I changed

| Setting | Value | Effect |
| --- | --- | --- |
| `global ungaze_enabled` | `0` | Stops the "look away to turn off screen" gesture |
| `global ungaze_sleep_enabled` | `0` | Same, for sleep |
| `secure wake_gesture_enabled` | `0` | No wake on wrist movement |
| `global master_gestures_enabled` | `0` | Master switch for wrist gestures |
| `global smart_illuminate_enabled` | `0` | No automatic screen wake |
| `global heads_up_notifications_enabled` | `0` | Notifications don't pop up and wake the screen |
| `global wear_activity_auto_resume_timeout_ms` | `600000` | **Wakes into the last app (e.g. Spotify) instead of the watch face**, for up to 10 minutes after the screen went off. The default is 30 s |

I kept tap-to-wake on (`secure double_tap_to_wake`, `global ambient_touch_to_wake`, `system touch_to_wake_switch`). Set them to `0` if the screen wakes in your pocket.

Optional: animations off (`global window_animation_scale`, `transition_animation_scale`, `animator_duration_scale` = `0`) and location off (`secure location_mode 0`).

### 3.3 Emergency SOS: the crown can call emergency services

Pressing the crown repeatedly (e.g. fiddling with it in a pocket) triggers **Quick SOS call**, which **dials the emergency number through your paired phone**. It happened to me.

- Turn it off in the watch's **Settings → Safety & emergency → Quick SOS call** (name may vary; search Settings for "SOS"). This writes `system emergency_dialer_enable=0`.
- Setting `secure emergency_gesture_enabled 0` (the standard Android key) did **not** stop it on this watch.

Think about whether you want this: turning it off removes a safety feature. If you keep it, be careful with the crown in a pocket.

If you trigger it by accident, call the emergency number back and tell them it was a mistake.

### 3.4 Things that did not work

- `secure lock_screen_lock_after_timeout`: ignored. The lock is driven by wrist detection, not by a timeout.
- There is **no setting** for wrist/off-body detection, and no "PIN only for Wallet" option. Wallet needs a screen lock, and Wear OS asks for the PIN again whenever it thinks the watch came off a wrist. Section 4 is the workaround.

## 4. Stop the lock when you lift the watch

### 4.1 The problem

The watch has a low-latency off-body sensor (`LLOB`, value 1 = on wrist, 0 = off). Lying still in a pocket or on a table it's a steady 0, which is fine. But when you **lift** it, the value flips 1 → 0 within milliseconds. Wear OS takes that as "taken off the wrist": the screen goes dark and the watch locks. In practice you type the PIN every time you take it out.

Four clients listen to the sensor (`adb shell dumpsys sensorservice`): the lock screen (`KeyguardOffBodyController`), Wallet (`tapandpay…WearableOffBodyDetector`), and two Wear OS services.

### 4.2 The workaround: sensor restrict mode

Android's sensor service has a built-in test mode that only lets apps whose package name contains a given string receive sensor data. Restrict it to a string nothing matches, and **no app gets sensor events**, so the lock screen never sees the flip:

```
adb -s <WATCH> shell dumpsys sensorservice restrict nomatch.anything     # on
adb -s <WATCH> shell dumpsys sensorservice enable                        # off
adb -s <WATCH> shell "dumpsys sensorservice | grep '^Mode'"              # check: RESTRICTED or NORMAL
```

Result: lifting the watch no longer locks it. What happens after that depends on how the watch was held when restrict mode switched on (see below).

Trade-offs:
- **All apps lose sensor data** while it's on: step counting, auto-brightness, wrist tilt. For a pocket player that's fine.
- **A reboot resets it.** The Pocket Player menu (section 5) can switch it from the watch itself, so you don't need a computer after a reboot.
- **Wallet:** opening Wallet works. A real payment in restrict mode was **not yet tested** at the time of writing. Wallet's own off-body detector is frozen too.
- **Security:** frozen "on wrist", the watch stays unlocked for anyone who picks it up, until you lock it with **Lock Watch** or it reboots.

**Restrict mode freezes the last wrist reading (found 2026-11-10).** The system keeps whatever the off-body sensor said just before restrict mode switched on:
- Switched on with the back of the watch against your wrist or palm: frozen **on wrist**. The watch never locks by itself and every wake goes straight to the last app, no PIN. Wallet opens; a payment probably works but is untested.
- Switched on while lying on a table: frozen **off wrist**. PIN on every wake.
- Pocket Player's menu shows which one you got ("OFF · frozen on wrist (no PIN)"). Frozen on wrist means anyone who picks the watch up can use it until you press **Lock Watch** or it reboots.

**Equalizer: not possible on this watch (checked 2026-11-10).** Its audio stack loads no Equalizer, BassBoost or DynamicsProcessing effect (only Qualcomm Audio Sphere, a volume listener, pre-processing and the Visualizer). The watch only does AAC and SBC over Bluetooth, no LDAC. For better sound: set the EQ in the headphone maker's app (some makers store it in the headphones), and check Spotify's download quality on the watch.

### 4.3 Dead ends, so you don't repeat them

- **Root:** the bootloader is locked, there's no published firmware to patch, and an unlocked bootloader fails Play Integrity, which **kills Google Wallet**. If you're willing to drop Wallet, you don't need root anyway: just remove the screen lock.
- **The lock-after-timeout setting:** ignored (3.4).
- **Physical fixes** (tape or a closed case back over the sensor) are an option I haven't tested. The issue is the on/off **transition**, so anything that keeps the sensor in one steady state should help.

## 5. Pocket Player (sideloaded app)

A small app written for this setup. Source in [`app/`](../app), package `org.op2r.pocketplayer`, version 0.7 (November 2026). It adds two entries to the app list, plus a watch face (5.6):

| Entry | What it does |
| --- | --- |
| **Pocket Player** | The screen you see every time you take the watch out (auto-resume, 3.2, wakes into it). Spotify controls over the album cover, with a "living" edge visualizer. Details below. |
| **Lock Watch** | No screen: turns the screen off and locks immediately. **Put it on a button shortcut** (5.3). |

### 5.1 The player screen

- **Background:** the album cover, dimmed, plus a soft dark layer behind the controls. New covers slide in from the right after *next*, from the left after *previous*, and crossfade otherwise; title and artist move with them.
- **Top:** title, artist and song time. Long titles and artists scroll sideways (marquee) and fade out along the round edge instead of being cut off straight.
- **Middle:** previous · play/pause · next. **Below:** − · ☰ menu · +, with − and + directly under the skip buttons (bigger touch targets). Buttons are 70 % opaque so the cover shows through.
- **Volume:** − and + change the media volume without the system pop-up; a small bar floats in for 1.5 s to show the level.
- **No Spotify session?** The play button sends a media "play" key, which Android routes to the last media app (Spotify). It starts and resumes its last queue, no need to open Spotify first.
- **Visualizer** (pick in the menu), all showing song progress clockwise from 12 o'clock:
  - **Layered waves** (default): three filled, layered waves hugging the edge. Alive where the song has played, a calm thin band ahead, soft tapers at both ends and at the current position.
  - **Full ring:** thin bars around the rim; the played part is packed twice as tight and bright.
  - **Lower arc:** bars along the bottom, filling from left to right.
  - **Small equalizer:** four small bars at the bottom, under the button rows (they used to sit above the title, which collided with the clock on the face), plus a progress ring.
  - They move randomly while playing and settle when paused, redrawn at 30 fps (see section 8). Each bar follows its own smooth random walk with its own tempo. They don't follow the actual audio; I tried that and removed it (section 7).
- **Colour:** white, or a colour taken from the cover (menu → Colour), used on the visualizer and the volume bar.

**The menu (☰)** is a see-through layer over the cover: Sensors on/off (with the frozen wrist state, 4.2), Lock now, Spectrum style, Colour, Open Spotify (for playlists), Connected devices (Bluetooth output).

### 5.2 Why album covers come from the internet

Spotify's media session on Wear OS only exposes **title, artist, album, duration and an ad flag**: no image, no image link, no track ID. Spotify's watch media browser connects but refuses to list anything for third-party apps, so there's no playlist access either.

So the app looks the cover up by **artist + album**, first on the Deezer search API, then on the iTunes search API. Neither needs an API key or account. Covers are stored on the watch as 320 px WebP files (about 5 KB each), so they work offline after the first online play. The cache is capped at 10 MB (oldest-played removed first) and covers not played for 90 days are deleted.

**Cover colour:** only colours that are really in the cover (Palette swatches with at least 3 % share). Muted swatches are preferred over neon ones, then brightened and given a bit more colour (saturation ×1.4, between 0.35 and 0.75). If the colour is the cover's own dominant hue, it gets a lighter tint so it doesn't disappear into the cover. Greyscale covers get a soft white. Hues are never shifted: "complementary" accents looked random on real covers.

### 5.3 Install

Download `pocket-player.apk` from the repository's releases if one is published, or build it (5.5). Then:

```
adb -s <WATCH> install -t -r pocket-player.apk

# 1. Read Spotify's media session (the app registers as a notification listener for this)
adb -s <WATCH> shell cmd notification allow_listener org.op2r.pocketplayer/org.op2r.pocketplayer.MediaListener

# 2. "Lock Watch" uses the device-admin lock (force-lock policy only)
adb -s <WATCH> shell dpm set-active-admin --user 0 org.op2r.pocketplayer/.AdminReceiver

# 3. The menu's Sensors switch runs restrict mode itself; this grant survives reboots
adb -s <WATCH> shell pm grant org.op2r.pocketplayer android.permission.DUMP

```

The `-t` flag is needed because the app is marked test-only. That's deliberate: it lets ADB remove the device-admin rights again, which Android requires before you can uninstall it:

```
adb -s <WATCH> shell dpm remove-active-admin --user 0 org.op2r.pocketplayer/.AdminReceiver
adb -s <WATCH> uninstall org.op2r.pocketplayer
```

### 5.4 Button shortcuts

In the watch's **Settings → Buttons** (name varies), point presses at the app. The values end up in `system` settings such as `navigation_double_shortcut_component` and `multifunction_single_shortcut_component`, if you'd rather set them by ADB. The app keeps the class names `MainActivity` (Pocket Player) and `LockActivity` (Lock Watch) stable so these shortcuts keep working across versions.

An example layout: one button's single press = Lock Watch and its double press = Wallet; the other button's double press = Pocket Player and its long press = Spotify.

### 5.5 Build it yourself

No Android Studio needed, everything installs in your user folder:

1. **JDK 17**, e.g. Microsoft OpenJDK zip, extracted anywhere. Set `JAVA_HOME` to it.
2. **Android command-line tools** from developer.android.com, extracted to `<SDK>/cmdline-tools/latest`. Then:
   ```
   sdkmanager --sdk_root=<SDK> --licenses
   sdkmanager --sdk_root=<SDK> "platforms;android-34" "build-tools;34.0.0"
   ```
3. In `app/`, create `local.properties` with `sdk.dir=<SDK>` (on Windows, escape it like `C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`).
4. Build and test: `./gradlew testDebugUnitTest assembleDebug` (Windows: `gradlew.bat`). The APK is `app/build/outputs/apk/debug/app-debug.apk`. The pure logic (song time, cover colour, visualizer maths, settings) has JVM unit tests.
5. Check a change on the real watch with a screenshot: `adb -s <WATCH> exec-out screencap -p > shot.png`.

### 5.6 Pocket Face (the same screen as a watch face)

Pocket Face is a coded watch face in the same APK (`PlayerFaceService`). It draws the player screen as the face itself, so every wake shows the controls, with no app to open and no accidental exit to a clock.

- **Same look as the app:** cover with the dark layer, title, artist and song time, the visualizer style and colour you picked in the menu, the same Figtree fonts, the same buttons. It reuses the app's drawing code (`SpectrumView`, `IconView`, `ArtFetcher`, `Accent`).
- **Clock:** small and bold, curved along the top rim, above the title (the app has none).
- **Buttons are tap zones.** Previous, play/pause, next, − and + work as in the app. **Taps are all a watch face receives:** swipes, long presses and edge gestures belong to the system. After − or + a small volume bar shows for 1.5 s, as in the app. The ☰ zone is meant to open the menu (`ToolsActivity`); starting an activity from a face wasn't tested on every state, so if it does nothing, open Pocket Player from the app list.
- **Locked (the watch asks for a PIN or pattern):** the face draws everything except the buttons and the volume bar, and ignores taps. The title, artist and song time sit a little lower, centred between the clock and the middle of the screen, which makes the locked state easy to spot. In practice the system draws its pattern pad over the face while locked, so the controls can't be used from a locked watch at all. With restrict mode on (section 4) the watch isn't locked on your wrist and the full face shows.
- **Always-on / dimmed:** a dim clock on black only.
- **Frame rate:** about 30 fps while music plays or anything is animating (cover slide, marquee, volume bar), otherwise twice a second.

**Select it:** it is listed as **Pocket Face** in the face picker, far down the list (OnePlus's own faces come first). Long-press the current face, swipe to the **+** card, scroll to it, tap it. From ADB you can open the same picker with `adb shell am start -n com.oplus.wearable.sysui/com.google.android.clockwork.sysui.mainui.module.watchfacepicker2.WatchFacePickerAllFacesActivity`, scroll with `input swipe`, and tap the face.

**What the spike showed (2026-11-12):**
- A sideloaded coded watch face is accepted: `adb install` is enough, and it appears in the picker. No signing tricks or special flags.
- Taps arrive as separate down and up events with exact coordinates, and can control Spotify through the media session (play/pause, previous, next) with the same notification-listener grant the app already needs.
- The face runs in the app's own process, so it can read the Spotify session, fetch covers and use the audio manager like any app.
- **Risk:** the AndroidX watch-face library used here is marked deprecated in favour of the declarative Watch Face Format, which cannot read a media session or run code. Google also requires the new format for Play Store distribution. Sideloading isn't covered by that, but a future Wear OS update could break a coded face. Keep Pocket Player as the fallback.

**Build notes:** `androidx.wear.watchface:watchface` and `watchface-guava` 1.2.1 (the APK grew to about 13 MB). The face is written in Java: extend `ListenableWatchFaceService` and `ListenableCanvasRenderer2`, return `Futures.immediateFuture(Unit.INSTANCE)` from `initFuture()` and an assets object from `createSharedAssetsFuture()`. The manifest service needs the `BIND_WALLPAPER` permission, the `android.service.wallpaper.WallpaperService` action with the `com.google.android.wearable.watchface.category.WATCH_FACE` category, an `android.service.wallpaper` meta-data pointing at an empty `<wallpaper/>` XML, and preview drawables. Views that are never attached can still be drawn into the face's canvas (`measure`, `layout`, then `draw(canvas)`); only the self-scheduled animation has to be turned off (`SpectrumView.setDrawnExternally`).

## 6. Checklist after setup

- [ ] Pocket Face is selected as the watch face (5.6), or Pocket Player is your auto-resume app
- [ ] Music plays offline over Bluetooth headphones with the phone away
- [ ] Waking the watch lands in Pocket Player (auto-resume, 3.2)
- [ ] Play with no Spotify session starts your last queue
- [ ] After a reboot: menu → Sensors → OFF, **with the watch against your wrist**, and check it says "frozen on wrist" (4.2)
- [ ] Lifting the watch doesn't lock it; the Lock Watch button does
- [ ] Wallet payment works (test once)
- [ ] Quick SOS call is off, or you've decided to keep it
- [ ] Wireless debugging and developer options are off again

## 7. Findings worth knowing (for anyone building something similar)

**Watch hardware and Wear OS**
- **No LDAC.** The watch's Bluetooth stack only offers AAC and SBC (check `dumpsys bluetooth_manager`, "local capabilities"), so LDAC headphones fall back to AAC. A phone using LDAC will sound better.
- **No equalizer possible.** The audio stack loads no Equalizer, BassBoost or DynamicsProcessing effect (`dumpsys media.audio_flinger`, "Libraries loaded"). Use the headphone maker's app if it stores the EQ in the headphones, and check Spotify's download quality on the watch.
- **The Visualizer effect works on the output mix (tested 2026-11-11), but wasn't worth keeping.** `new Visualizer(0)` with `RECORD_AUDIO` (granted over ADB) returns real FFT data while Spotify plays over Bluetooth, and zeros when paused. I built a music-reactive visualizer on it (24 log-spaced bands, per-band auto gain, 30 Hz polling) and removed it again: the bars barely moved in a way you could read as music, and it needed a microphone-class permission. The steady rhythm looks as good. If you try it, FFT bytes are laid out as DC, Nyquist, then real/imaginary pairs.
- **Button shortcuts don't run over the PIN pad.** OnePlus's button handler (`ShortcutKeyCtrl`) checks `isLocked` and hands the press to the lock screen ("unlock first") before your app is ever started, so `showWhenLocked` can't help. On the PIN pad the watch is already locked anyway; the screen just stays lit until it times out.
- **The lock follows the wrist sensor, not the screen.** See 4.1 to 4.2. Restrict mode freezes the last reading; that's why the PIN behaviour depends on how you hold the watch when you switch it on.
- **Quick SOS** (repeated crown presses) dials emergency services through the paired phone. The working switch is the watch's own Settings toggle (`system emergency_dialer_enable`), not Android's `emergency_gesture_enabled`.

**Spotify on Wear OS**
- Its media session carries no art and no track ID; its media browser refuses other apps (5.2).
- A media "play" key (`AudioManager.dispatchMediaKeyEvent(KEYCODE_MEDIA_PLAY)`) starts Spotify and resumes its last queue even when it isn't running.
- Opening Spotify from another app: `getLaunchIntentForPackage` returns null because of Android 11+ package visibility. Start its activity explicitly instead: `com.spotify.music/com.spotify.wear.main.MainActivity`.
- The session only reports the position when playback changes. Extrapolate it yourself (position + elapsed × speed) every frame for smooth progress.

**Android gotchas met while building**
- A **see-through activity on top** (the menu) leaves the activity below started, so its `onStart` doesn't run again when you come back. Listen for setting changes (`SharedPreferences.OnSharedPreferenceChangeListener`) instead of re-reading them in `onStart`.
- **Marquee only scrolls when the text overflows its own box.** On a round screen, size each line to the visible width at its height (minus a margin), or long text sits still with its ends hidden by the curve.
- **Round-edge fades:** draw the text block into a layer and mask it with a radial gradient (`PorterDuff.Mode.DST_IN`), so text dissolves along the curve.
- **Shadows on custom-drawn icons** need `setLayerType(LAYER_TYPE_SOFTWARE)` for `Paint.setShadowLayer` to work on paths.
- **Granting `android.permission.DUMP` over ADB** lets an app call `dumpsys`-style service dumps itself (used for the Sensors switch and for reading the wrist state).
- **Text along a curve (`Canvas.drawTextOnPath`) ignores `Paint.Align.CENTER`:** the text is placed from the start of the path, so my centred clock landed at 3 o'clock. Use `Align.LEFT`, measure the text and start at half the arc length minus half the text width.
- **Lock state on a watch face:** `KeyguardManager.isDeviceLocked()` is true while the watch wants a PIN; use it to hide controls. `DrawMode.AMBIENT` marks the dimmed always-on state.
- **Changing app settings by editing the file behind the app's back doesn't trigger the app's settings listener.** When testing a style change with `run-as ... sed` on `shared_prefs/player.xml`, force-stop the app afterwards or nothing updates.

## 8. Battery (measured on the watch, 2026-11-11)

The watch's battery is 500 mAh (the fuel gauge learned 533). Over about 2.3 days on battery, `dumpsys batterystats` showed **1,013 mAh drawn: almost two full charges**, roughly 27 hours per charge. Much of that period was development (ADB, screenshots, animations tested at full speed), so treat the split as an upper bound.

**Where it went** (estimates from `dumpsys batterystats`, section "Estimated power use"):

| Consumer | mAh | Share | What it is |
| --- | --- | --- | --- |
| Spotify | 250 | 25 % | 7 h 17 min of playback: audio path 182, CPU 45, wakelocks 8.5, screen 15 |
| Android system (uid 1000) | 113 | 11 % | mostly CPU, including compositing the player's animation |
| Pocket Player | 72 | 7 % | screen 37, CPU 35 (before the 30 fps cap) |
| Kernel (uid 0) | 37 | 4 % | CPU |
| System UI, Google Play services, the rest | ~100 | 10 % | |

**Screen on is 8 % of the time but about half the discharge** (4 h 39 min on, 298 times, average 56 s per wake; 114 mA average while on, against 9 mA screen-off including music). Most wakes ran to the full 60 s timeout.

**What helps, in order of effect:**
1. **A shorter screen timeout** (`settings put system screen_off_timeout 15000`), or press Lock Watch when you pocket the watch: it turns the screen off at once. This is the biggest lever, because every wake otherwise runs to the timeout.
2. **Cheaper visualizer styles.** Measured CPU of the player alone at 30 fps, with the screen on and music playing: small equalizer 0.19 cores, lower arc 0.34, layered waves 0.43, full ring 0.50 (a static screen costs about 0.01).
3. **Cap redraws at 30 fps** (done): measured 44 to 47 % less CPU in the player and half the screen-compositor work, in every style. See the gotcha below.
4. **Turn wireless debugging (and Wi-Fi when out) off** when you aren't developing. With it on, the Wi-Fi chip woke the CPU 8,277 times and its kernel wakelock was taken 124,000 times in 2.3 days; idle drain screen-off was about 6 mA, where a watch should idle near 2 mA.
5. Music itself is mostly inherent: about 25 mA while playing, even though the Bluetooth encoding is offloaded to the audio chip (`dumpsys bluetooth_manager`: `A2dpOffloadEnabled: true`).

**How to measure on a watch**
- The fuel gauge's charge counter (`dumpsys battery`) only moves in 1 % steps (about 5 mAh), so it's useless for short tests. Measure **CPU time** instead: read `/proc/stat` and `/proc/<pid>/stat` over 30 s (`utime + stime` in 1/100 s ticks) for the whole system, the app and `surfaceflinger`, with the screen on and the same music playing. Compare against a static screen as the floor.
- Count frames with `dumpsys gfxinfo <package> reset`, wait 10 s, then read "Total frames rendered" (about 60/s at full speed, 30/s when capped).
- For real-world numbers: `dumpsys batterystats --reset`, use the watch normally for a day with wireless debugging off, then read it again.

**Gotcha: a "30 fps cap" can silently do nothing.** Calling `postInvalidateDelayed()` from `onDraw` starts a new chain of redraws every time anything else calls `invalidate()`; the chains pile up and the view draws at 60 fps again (measured: 586 frames in 10 s). Allow only one scheduled frame at a time. And a 33 ms delay misses every second screen refresh by a hair (measured: 22 fps); `postOnAnimationDelayed(…, 20)` lands on every second refresh, which is exactly 30 fps.

**Things I measured that didn't pay off, and one that would**
- **Round-edge text fade** (offscreen mask over the title block): switching it off changed the CPU by less than the measurement noise. Keep it.
- **A new gradient object per bar per frame** (ring and arc): drawing solid bars instead cut the player's CPU by 29 % (ring, 0.45 to 0.32 cores) and 24 % (arc, 0.33 to 0.25). It would pay off, but a shared gradient can't give each bar its own fade length, so the look changes slightly. Reusing one shader with `setLocalMatrix` doesn't help: it discards the native shader each time.
- **Count of screen wakes** (298): not usable when you develop over ADB, because every `input keyevent 224` from the computer is a wake. Reset the stats and measure a normal day instead.
- **A rhythm that looks like a pattern:** a sine per bar, with the frequency chosen from `bar % 5`, repeats itself almost exactly (autocorrelation 0.99) and bars five apart are near copies (0.89), which reads as a wave. Per-bar smooth random targets measure 0.18 and 0.13. Test for it with correlations, not by eye: checking only neighbouring bars gave 0.01 for the old pattern and missed it.


## 9. Headphones connect but there's no sound (a watch-side Bluetooth audio fault)

**Seen on 2026-11-12**, after the watch had been in use for days, with two different pairs of Bluetooth headphones from different makers. It started after the watch had been asleep, and it came and went for several hours. Pairing was fine every time.

**What you notice (any of these):**
- The headphones connect (even "cleanly"), but Spotify can't play, or it plays and you hear nothing.
- Play starts, then the headphones disconnect about a second later and the music moves to the **watch speaker**.
- Spotify's **Connect a device** button flashes and closes, or the watch shows as "unavailable" in a phone's Spotify device list.
- Putting the headphones in their case and taking them out again auto-connects them, but play does nothing.

**What the log shows** (`adb shell dumpsys bluetooth_manager`, and `adb logcat`):
- The headphones sometimes reconnect with only the **call profile**: under `Profile: HeadsetService` the device is connected, but `=== A2dpStateMachine for ... ===` says `DISCONNECTED` and `A2dpService mActiveDevice: null`. Nothing can route media to them. Spotify logs `timeout waiting for audio output` when you press play.
- When A2DP does connect, the A2DP state machine records `AUDIO_STATE_CHANGED STARTED` and, **within 30 to 100 ms**, the connection goes `Connected -> Disconnecting -> Disconnected`. The audio driver logs `PAL: Bluetooth: startPlayback: BT controller start failed`, then `AHAL: failed to start stream` and `write error -22`.
- The watch uses hardware offload for Bluetooth audio (`A2dpOffloadEnabled: true`); the audio chip, not the CPU, encodes the stream. The failure is in starting that stream, so it is on the watch, not in the headphones, Spotify or any sideloaded app.
- A second pair of headphones failing the same way is the quickest proof that it is the watch.

**What fixed it:** a **full power-off, wait about 30 seconds, then power on.** After that both headphones worked. This reset the Bluetooth chip and audio processor, which a software restart did not.

**It came back once.** About four hours after the first cold start, a pair of headphones wouldn't play again (I didn't capture the log that time) and a second cold start cleared it. After that, the case-and-out cycle (headphones into the charging case, watch locks, case opened again) reconnected cleanly: A2DP came up within milliseconds, audio started and stayed on, and `BT controller start failed` never appeared in the log. So the fix isn't permanent, but each cold start has worked.

**What did not fix it (in order tried):**
- Re-pairing the headphones, and tapping them in the Bluetooth settings to reconnect (that worked once for a few minutes, then failed again). I also suspected earbud multipoint, but never tested turning it off.
- `adb shell svc bluetooth disable` then `enable` (restarts the Bluetooth stack). It once brought sound back for a few minutes, then failed again; the next time it only produced "connected, playing, silent".
- `adb reboot` (a warm restart): the next connection attempt failed again with both headphones.
- Re-enabling `com.oplus.wearable.media.sessions` (2.3), the one system package I had disabled recently. It made no difference.

**What I could not do:** turn hardware offload off. The property `persist.bluetooth.a2dp_offload.disabled` can't be set from the ADB shell on this build, and the watch's Settings has no switch for it. If you find a way, it's the obvious next thing to try if the cold start stops working.

**Cause: not found.** Storage looked healthy (no filesystem errors; the `I/O error` lines from `artd` are only missing optimisation files). A firmware bug in the Bluetooth audio path is my best guess. Check for a watch update (Settings → System → System update); a factory reset would be the last resort.

**Is a package corrupted? Probably not.** Toggling Bluetooth off and on (Settings, or `adb shell svc bluetooth disable` then `enable`) fixed it several times, which fits a state problem in the running Bluetooth stack more than a damaged file, because a damaged file would survive a restart. Bluetooth here is a mainline module (`com.android.btservices`, read-only, signed and verified at boot), so file damage would normally stop it loading altogether, not make it misbehave now and then. One real finding: `adb shell dumpsys dropbox --print` showed a native crash of the Bluetooth process (`SIGSEGV`, null pointer) at the moment Bluetooth was starting, inside the AVRCP media-control code (`AvrcpService::SendMediaUpdate`, reached from `MediaPlayerList.setActivePlayer` while the profiles start). It happened once, on 2026-11-12 after the first drops, so it may be a symptom, not the cause. If you want to test the corruption idea, clearing Bluetooth's data (`pm clear com.android.bluetooth`) would show it, but it deletes every pairing, including the phone link, so it's a last step before a factory reset.

**Quick checks when it happens again**
1. `adb -s <WATCH> shell dumpsys bluetooth_manager | grep -E "=== A2dp|=== Headset|mConnectionState|mActiveDevice|mIsPlaying"` shows whether A2DP is connected and active.
2. Press play (`adb shell input keyevent KEYCODE_MEDIA_PLAY`) and read `adb logcat -d | grep -E "PAL|AHAL"` for `BT controller start failed`.
3. If it fails: power the watch off completely, wait 30 seconds, switch it on, reconnect the headphones, test.

Until it's fixed, Spotify on a phone or laptop can still be controlled from the watch, and the watch speaker plays if no headphones are connected.
