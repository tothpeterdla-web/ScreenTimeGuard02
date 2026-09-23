# Screen Time Guard

**Screen Time Guard** is an Android digital-wellbeing and self-control app designed to make daily screen-time limits difficult to bypass while keeping essential phone functions available.

It combines a custom local screen-time counter, Android Device Owner capabilities, restricted-mode app allowlisting, guardian-PIN controls, AdGuard protection, anti-reset safeguards, and a custom dark interface built around a blue mascot.

> **Important:** the strongest protection features use Android **Device Owner** APIs. Installing the APK alone does not automatically enable Device Owner mode. Device Owner provisioning has to be performed separately on a compatible Android device, typically during device setup or through ADB for development/testing.

## Why this project exists

Many screen-time apps can be disabled simply by revoking a permission, uninstalling the app, changing the clock, or opening another browser or app store. Screen Time Guard was built around a different idea: if the user chooses strong protection, the protection itself should be harder to casually bypass.

At the same time, the app tries to preserve recoverability. Earlier versions experimented with fail-closed behavior, but the current design intentionally avoids trapping the user if a measurement prerequisite fails. The present screen-time tracker no longer depends on Android Usage Access.

## Main features

### Local daily screen-time tracking

Screen Time Guard measures screen time locally while the phone is unlocked and interactive.

- No `PACKAGE_USAGE_STATS` / Usage Access permission is required.
- The counter is stored locally and survives normal app restarts and phone reboots.
- The daily counter resets by date.
- The measurement is intentionally based on unlocked interactive phone time, so it may be slightly higher than Digital Wellbeing because launcher/system-UI time can also be counted.

### Daily limit and restricted mode

A daily screen-time limit can be configured in hours and minutes.

When the limit is reached, the app uses Android Lock Task / Device Owner policies to enter a restricted mode in which non-approved apps are unavailable.

The goal is to preserve useful phone functionality while preventing normal distraction apps from being opened after the limit.

### Allowed apps after the limit

The app supports an allowlist for apps that should remain available after the daily limit is reached.

Built-in exceptions currently include:

- Google Maps
- BudapestGO
- the default dialer / phone app
- the launcher / home app
- Screen Time Guard itself

Additional apps can be selected from inside the app.

### Browsers and app stores blocked after the limit

Common browsers and app stores are treated as always-blocked packages in restricted mode so they cannot be used as easy workarounds.

The current block list includes common packages for Chrome, Firefox, Edge, Opera, Brave, Vivaldi, DuckDuckGo, Google Play Store, Samsung Galaxy Store, Huawei AppGallery and several OEM app stores.


### App installation gate

App installation is controlled independently of the daily screen-time limit. When installations are disabled, the Device Owner policy applies `DISALLOW_INSTALL_APPS`, so apps cannot be installed from Google Play, other app stores, or APK files. Unknown-source installation is also blocked explicitly.

A guardian can enable installations from the **App installation control** screen using the guardian PIN. Installations then remain enabled until they are manually disabled again. Ordinary file downloads are not affected.

App stores remain blocked during restricted mode as part of the separate screen-time anti-bypass policy.

### Messenger in-app link guard

Messenger can render links in its own WebView instead of launching a separate browser package. Screen Time Guard includes an optional Accessibility service that watches only Messenger (`com.facebook.orca`) and, while restricted mode is active, immediately closes Messenger-hosted web pages while leaving normal chats usable.

This service must be enabled once in Android Accessibility settings. Screen Time Guard includes a **Messenger link blocker** button that opens the correct system settings page.

### Guardian PIN

Sensitive actions are protected by a guardian PIN, including:

- changing the daily limit
- changing allowed apps
- guardian unlock until midnight
- disabling screen-time protection
- changing the guardian PIN
- releasing Device Owner mode

### Guardian unlock until midnight

A guardian can temporarily unlock the phone until midnight without permanently disabling the configuration.

### Device Owner protection

When properly provisioned as Android Device Owner, Screen Time Guard can use stronger device-management policies than an ordinary Android app.

Current Device Owner features include:

- blocking uninstall of Screen Time Guard itself
- blocking uninstall of AdGuard
- blocking factory reset from Android Settings
- blocking reboot into Android Safe Mode for as long as Screen Time Guard remains Device Owner
- preventing date/time changes while strong screen-time protection is active
- configuring Lock Task packages and features for restricted mode

Normal third-party apps are intentionally **not** globally protected from uninstall.

### Safe Mode anti-bypass protection

When Screen Time Guard is Device Owner, it permanently applies Android's `DISALLOW_SAFE_BOOT` restriction. This policy is independent of the daily screen-time limit and remains active even when screen-time protection is turned off or a guardian override is active.

The policy is restored on boot, on app replacement, when Device Owner is enabled, and continuously by the foreground enforcement service. It is removed only as part of the explicit guardian-authorized Device Owner release flow.

This closes the Safe Mode loophole where Android would otherwise disable ordinary third-party apps such as Screen Time Guard and AdGuard during a Safe Mode boot.

### AdGuard integration

The project is designed to work alongside the Android AdGuard app for adult-content filtering.

Screen Time Guard does not replace AdGuard. Instead, when Device Owner mode is active, it can protect AdGuard from uninstall so filtering cannot be removed casually.

The current setup has been tested with AdGuard Family Protection DNS configured inside AdGuard.

### Quick controls inside restricted mode

Because Android Lock Task mode can limit access to the normal Quick Settings panel, Screen Time Guard provides its own quick controls for common actions:

- Wi-Fi
- Bluetooth
- mobile-data settings panel
- screen brightness
- auto rotation

Android does not expose a public Device Owner API that allows arbitrary direct toggling of mobile data on modern devices, so the app opens Android's Internet panel for that function.

### Factory-reset protection

When Screen Time Guard is Device Owner, it applies `DISALLOW_FACTORY_RESET`, which blocks the normal factory-reset flow in Android Settings.

This does **not** guarantee that every hardware/recovery-level wipe method is impossible. Recovery environments and OEM-specific reset mechanisms may behave differently.

### Dark custom interface

The app has a custom dark UI with:

- black / deep-navy background
- blue accent color
- glass-style cards
- daily screen-time and remaining-time summary
- progress indicator
- protection-status cards
- quick controls
- grouped safety and maintenance controls
- a custom blue-line mascot
- adaptive black-and-blue launcher icon

## Installation

### Normal APK installation

Download a **signed APK** build and install it on Android.

Because the app is not currently distributed through Google Play, Android may ask you to allow **Install unknown apps** for the browser or file manager used to open the APK.

A normal APK install gives you the application UI, local timer and basic functionality, but it does **not** automatically make the app Device Owner.

### Updating an existing Device Owner installation

If Screen Time Guard is already installed and provisioned as Device Owner, update it by installing a newer APK signed with the **same release signing key** over the existing installation.

Do **not** uninstall the existing app first. Uninstalling/reinstalling would break the current Device Owner relationship and configuration.

### Device Owner provisioning for development/testing

For a development/test device that is eligible for Device Owner provisioning, the receiver is:

```text
com.example.screentimeguard/.GuardDeviceAdminReceiver
```

A typical ADB development command is:

```bash
adb shell dpm set-device-owner com.example.screentimeguard/.GuardDeviceAdminReceiver
```

Android only allows Device Owner provisioning under specific device/account/setup conditions. If Android rejects the command, the device may need to be freshly set up or otherwise meet Android's Device Owner requirements.

## Safety notes

Screen Time Guard uses powerful Android management APIs. If you are experimenting with Device Owner and Lock Task behavior, test changes carefully.

Recommended development practice:

- keep strong protection disabled while testing new builds
- install updates over the existing signed app
- verify that Screen Time Guard itself remains reachable before enabling stricter policies
- keep a recovery path available
- do not rely on the app as the only way to recover important data from the device

The project deliberately avoids blocking the entire Android Settings app because doing so can create unsafe lockout scenarios.

## Current limitations

- Local screen-time measurement can differ from Google's Digital Wellbeing because it counts unlocked interactive time rather than Digital Wellbeing's private/system-level accounting.
- Home-screen and some System UI time can therefore be included.
- The strongest anti-bypass features require Device Owner provisioning and are not available from an ordinary install alone.
- Hardware/recovery factory resets cannot be universally guaranteed to be blocked.
- In-app browsers inside allowed applications may still create web-access loopholes depending on the app.
- Android OEM behavior can vary, so Lock Task and management policies should be tested on each device family.

## Tested device

Development and real-device testing have primarily been performed on an ASUS Zenfone 8 running Android.

Because Android Device Policy behavior can vary by manufacturer and OS version, users should treat support on other devices as experimental until tested.

## Technical overview

The project is an Android app written in Java.

Important components include:

- `MainActivity` — dashboard, settings, guardian controls and quick controls
- `ScreenTimeService` — foreground service that continuously evaluates screen-time state and enforcement
- `ScreenTimeTracker` — local unlocked/interactive-time accounting
- `PolicyUtils` — Device Owner policies, Lock Task configuration, uninstall protection and factory-reset protection
- `Prefs` — local configuration and state
- `PinStore` — guardian PIN handling
- `QuickControls` — Wi-Fi, Bluetooth, brightness, rotation and Internet-panel helpers
- `BootReceiver` — restores required services/policies after boot and app replacement
- `GuardDeviceAdminReceiver` — Android Device Admin / Device Owner receiver

## Privacy

The current screen-time counter is local to the device. The project does not need Usage Access to read per-app usage history.

Before distributing the app broadly, review the code and your final distribution configuration and publish an appropriate privacy policy for any functionality you add later, especially if analytics, crash reporting, cloud sync, accounts or remote guardian features are introduced.

## Status

This is an actively developed personal project and should currently be considered experimental software.

It has grown from a simple screen-time limiter into a Device Owner based digital-wellbeing tool with custom anti-bypass protections and a purpose-built interface.

Contributions, testing reports and device-compatibility feedback are welcome.
