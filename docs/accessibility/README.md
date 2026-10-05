# Accessibility release gate

Static review finds native semantics and explicit player-button labels on both clients, 44 pt iOS and 48 dp Android target conventions in some components. This is not proof that all controls meet them. Android contains hardcoded German labels; iOS child mode forces dark, and animations are not uniformly tied to Reduce Motion.

Before release, run VoiceOver and TalkBack through setup, wrong/locked PIN, profiles, pending approval, empty list, search, playback, error, exit and relock. Confirm focus order, modal isolation, clear control names and no duplicate announcements. Test maximum Dynamic Type/font scale and display scaling, landscape, small Sidephone display, light/dark settings, increased contrast and reduced animation settings. Measure contrast for actual foreground/background pairs and disabled/focused states.

Record device/OS, commit, locale, accessibility setting, steps, expected/actual behavior and screenshots with synthetic profiles only. Check touch targets at least 44 pt (iOS) / 48 dp (Android) where applicable, without using visual icon size as a substitute for hit-area measurement. Audit WebView content separately from native labels.

Status: BLOCKER for a worldwide accessibility-readiness claim; no assisted-device walkthrough has been completed. Fixes must preserve hardware-key operation and respect native navigation conventions.
