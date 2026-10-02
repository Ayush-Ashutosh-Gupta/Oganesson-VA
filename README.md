# Oganesson

Personal Android voice assistant focused on speed.

Oganesson is built around one idea: simple phone commands should execute immediately on-device instead of waiting on a cloud round-trip like many stock assistants.

It listens for a wake phrase, shows a floating assistant overlay, parses common commands locally, and only uses cloud AI when the request is actually complex.

> Personal-use project. Shared for portfolio and demonstration.

## Why it exists

Stock assistants often feel slow even for direct actions like:

- call someone
- open an app
- set a timer
- change volume/brightness
- send a quick message

Oganesson prioritizes a **local-first command path** for those everyday actions.

## Core features

- Custom wake word support
- Default wake style around uncommon phrases like `Oganesson` / `Og`
- Floating Siri/Bixby-style overlay over the current screen
- Local command parser for common actions
- Compound commands such as open app + volume change
- Call, SMS, WhatsApp, notes, timers, alarms, app launch, search, maps
- Contact disambiguation when multiple matches exist
- Custom voice shortcuts with zero AI round-trip
- Advanced `CUSTOM_INTENT` mappings for power users
- Thermal and battery-aware behavior
- Eco / balanced power modes
- Persistent or session-based listening controls
- Cloud fallback through Mistral for complex queries
- Primary + secondary API key rotation support

## Design principles

1. Local first
2. Cloud only when needed
3. Fast acknowledgment through overlay UI
4. Recover cleanly when matches fail
5. Survive real Android conditions, not just demos

## Test device note

Developed and stress-tested on a Samsung Galaxy A52 class device:

- older mid-range hardware
- aging battery profile
- aggressive OEM background limits

The goal was to keep active use practical on a hard device, so newer phones should generally feel smoother.

## Project status

- Core assistant loop: working
- Local commands + overlay + wake flow: working
- Thermal/battery controls: implemented
- Complex AI path: present, still being refined for personal use

## Tech stack

- Kotlin
- Jetpack Compose
- Android Speech Recognition / TTS
- Room
- Foreground service + system overlay
- Mistral API client with key rotation

## Setup

### Requirements
- Android Studio
- Android device or emulator
- Microphone, overlay, and related permissions on device

### Run
1. Open the project in Android Studio
2. Let Gradle sync finish
3. Copy `.env.example` to `.env` only if needed for local secrets
4. Do not commit real API keys
5. Run on a physical device for best wake-word and overlay testing

### Important permissions
- Microphone
- Display over other apps
- Contacts / phone / SMS depending on features used
- Notification permission on newer Android versions

## Safety / privacy notes

- No API keys should be hardcoded in the repository
- User-provided keys stay on device preferences
- This is a personal assistant project, not a production consumer release

## License

© 2026 Ayush Ashutosh Gupta. All Rights Reserved.

This repository is shared for portfolio and demonstration purposes only.  
No permission is granted to copy, modify, distribute, or use this code without explicit written consent.
