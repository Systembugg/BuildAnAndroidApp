# Capsule

The Dynamic Island for Android, built for the iQOO Neo 10R (OriginOS). Native Kotlin and Jetpack Compose.

- **Shape and motion** are ported from [islandcn](https://github.com/opencoredev/islandcn), which measured the iPhone 18 Pro simulator frame by frame: the smaller iOS 27 island, its spring for every transition (with the via-idle turns), the split with its liquid bridge, the press swell and the 0.216 s long-press.
- **Content** is the Figma kit *iOS17 Dynamic Island Components*: every component in its minimal, compact and expanded states, using the kit's coordinates, colours, type sizes and SF Symbol outlines.

## Install

Open `android/` in Android Studio and run it, or build from the command line:

```bash
cd android
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
adb install app/build/outputs/apk/release/app-release.apk
```

Then open Capsule → System and go through the checklist (the accessibility host, notification access, unrestricted battery). On Android 13+ you first need App info → ⋮ → **Allow restricted settings**.

## Where things live

| Path | What |
| --- | --- |
| `core/Metrics.kt`, `core/Motion.kt` | islandcn's geometry and springs (response / damping → Compose stiffness) |
| `shape/IslandPath.kt` | Continuous corners and the metaball bridge |
| `ui/island/IslandSurface.kt` | The animated island: edges, bubble, crossfading content layers, gestures |
| `ui/island/Layout.kt` | Compact, split and expanded layout rules, and which transition to use |
| `ui/activities/Render.kt` | Every kit component |
| `ui/kit/` | Glyphs (outlined from the kit), text, rings, waveforms, the animated padlock |
| `engine/` | Activity stack: priorities, groups, alerts, timeouts |
| `system/` | Window host, accessibility/overlay services, broadcasts, notification and media listeners |
| `tools/svg_to_vector.mjs` | Regenerates `res/drawable/sf_*.xml` from the kit's outlines |

## Screenshot tests

`./gradlew :app:recordPaparazziDebug` renders every component in every state to `app/src/test/snapshots/images/` (no device needed).

## Automation

```bash
adb shell am broadcast -a dev.capsule.island.action.SHOW -n dev.capsule.island/.system.AutomationReceiver --es type timer --ef value 90
adb shell am broadcast -a dev.capsule.island.action.END  -n dev.capsule.island/.system.AutomationReceiver --es type timer
```

`type` is any Stage component (music, shortcut, findmy, satellite…); optional `title`, `subtitle`, `value`, `alert`.

## Notes

- Glyphs are SF Symbols outlined from the Figma file, and Inter stands in for SF Pro. Apple licenses SF Symbols for Apple platforms only, so keep this build personal; swap `ui/kit/Glyphs.kt` before publishing anywhere.
- Android doesn't tell apps whether you unlocked with your fingerprint or your face, so every unlock plays the padlock.
