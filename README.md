# OnePlus Watch 2R pocket music player

A OnePlus Watch 2R used as a pocket music player instead of a wrist watch. It plays offline Spotify over Bluetooth headphones, shows the time, and still pays with NFC through Google Wallet. The project has two parts: software for the watch, and a 3D-printed case that holds it in a pocket or on a lanyard.

Everything on the software side works without root, and every change can be undone.

## What is in the repository

| Folder | What it is |
| --- | --- |
| [`app/`](app) | The Android app for the watch (Java, Gradle). See [`app/README.md`](app/README.md) |
| [`model/`](model) | The parametric case, as Python scripts that run inside Blender. See [`model/README.md`](model/README.md) |
| [`docs/`](docs) | The two step-by-step guides: [software](docs/watch-software.md) and [3D-printed case](docs/3d-printed-case.md) |

## Software

The watch is set up from a computer over wireless ADB. The [software guide](docs/watch-software.md) walks through it in order.

- **Pocket Player** is a small sideloaded app. It shows Spotify's controls over the album cover, with the song time, a volume control and a moving visualizer along the screen edge (four styles). A menu holds the sensor switch, an instant lock and the Bluetooth device list. Spotify's media session carries no picture, so covers are looked up online by artist and album and then cached on the watch.
- **Pocket Face** is the same screen as a watch face, so the controls are there every time the watch wakes. A watch face only receives plain taps, so the buttons are tap zones.
- **Lock Watch** is a shortcut with no screen of its own. It turns the display off and locks the watch at once, and is meant for a button.
- **Debloating** removes the apps a pocket player does not need (health, sport, demo and telemetry packages), without breaking music, Bluetooth, Wallet or the clock.
- **Hidden settings** stop the watch waking from wrist movement and pop-up notifications, make it wake into the last app for up to ten minutes, and switch off the Quick SOS call that repeated crown presses can trigger in a pocket.
- **Sensor restrict mode** freezes the wrist sensor's last reading. Without it the watch locks and asks for a PIN every time it is taken off a wrist, which is every time it comes out of a pocket.
- **Findings** from building it: the watch's Bluetooth offers only AAC and SBC (no LDAC), no equalizer can be added, and there are a few Spotify and Android quirks. There is also a write-up of a Bluetooth audio fault on this watch where headphones connect but stay silent, and a full power-off clears it.

Tested on a OnePlus Watch 2R (model `OPWWE234`), Wear OS on Android 14, build `OPWWE234_11_A.192` (`AW2A.240903.001.A3.OPWWE234_11_A.192.260910`, security patch 2026-08-01), Spotify 9.1.98. Other builds may name settings differently, and the guide says how to look them up.

## The case

The case is described by a table of numbers (`model/params.py`). Python scripts run in Blender in the background, so the Blender window is never opened, and write one STL file per part, plus preview images and a report that checks sizes and wall thickness. Change a number, rebuild, and the parts update. A second script, `verify.py`, assembles the case virtually (pins in, watch in, back cover on, charger puck in its window) and checks for collisions and material too thin to print.

Two models are kept and maintained:

| Model | What it is | Size (W x H x T) |
| --- | --- | --- |
| **D2, `backsnap`** | Rounded rectangle hugging the watch. One-piece body with a snap-on back cover. Passes all 28 assembly checks | 53.0 x 57.2 x 14.05 mm |
| **E3, `snap`** | A longer pill shape. Two pieces, a front plate and a back shell, that snap together | about 63 x 86 mm, 14.5 mm thick |

The script still accepts the other shapes, closures, back types and edge profiles, but only the two models above are exported and checked.

Design rules:

- Thin, even walls around the watch, with the screen centred.
- The side buttons are reached through windows in the wall, using small printed push-pins.
- The watch charges on its own magnetic puck through a window in the back.
- No metal near the back, so NFC payments work through the case.
- Every part prints face down without supports.

Printing was done on a Creality Ender-3 V2 Neo with OrcaSlicer. Fit tests are printed in PLA and the final case in PETG, after a tolerance test that sets the gap between parts.

## Getting started

1. Clone the repository.
2. For the watch, follow the [software guide](docs/watch-software.md), then build and install the app as described in [`app/README.md`](app/README.md).
3. For the case, install Blender 5.0 and follow [`model/README.md`](model/README.md). The [case guide](docs/3d-printed-case.md) covers printer calibration, the watch measurements and the print order.

## Status

- **Software:** in daily use. Debloating, settings, restrict mode, the app, the watch face and Wallet payments are tested on the author's watch.
- **Case:** the model is built from measurements of the real watch. Early fit tests failed and were fixed, and the D2 shape is the one being refined. No finished case has been printed yet. The snap closures and the push-pins are not yet confirmed on a real print.

## Safety notes

- You change system settings and remove system apps at your own risk. Read the guide's "do not remove" list first.
- The watch's Quick SOS call can dial emergency services through the paired phone if the crown is pressed repeatedly in a pocket. The guide shows where to turn it off. Turning it off removes a safety feature, so decide for yourself.
- Sensor restrict mode keeps the watch unlocked for anyone who picks it up, until you press Lock Watch or the watch restarts.

## Licence

Licensed under the [GNU General Public License, version 2](LICENSE). Third-party parts keep their own licences: the Figtree font in the app is under the SIL Open Font License 1.1.
