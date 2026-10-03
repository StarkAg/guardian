<div align="center">

# 🛡️ Guardian

### The anti-theft app a thief **can't disable**.

Lost or stolen Android phone? Text it a secret code. Guardian **turns GPS and mobile data back on by itself** — even if the thief switched them off — gets a fix, and texts the location straight back to you. No cloud, no account, no subscription. Everything runs over SMS.

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-brightgreen)](https://developer.android.com)
[![Language](https://img.shields.io/badge/kotlin-100%25-blueviolet)](https://kotlinlang.org)
[![No cloud](https://img.shields.io/badge/servers-0-informational)](#privacy-no-servers-no-account-no-tracking)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-blue.svg)](CONTRIBUTING.md)

</div>

---

## The problem with every other "find my phone"

The first thing a thief does is turn off location and mobile data. At that point Google Find My Device, Life360, and most open-source apps are **blind** — the phone can't be located and can't phone home.

**Guardian is different.** Using [Shizuku](https://shizuku.rikka.app/) (no root needed) or `su`, it can **re-enable GPS and mobile data remotely**, from a single text message. The thief turned location off; Guardian turns it back on and tells you where the phone is. And when they swap in a new SIM, Guardian **texts you the new number** so you can keep following it.

That's the whole point of the project: not *another* locator, but the one that still works after a thief has tried to go dark.

---

## ✨ Features

| | |
|---|---|
| 🔓 **Re-enables GPS & data remotely** | Shizuku/root flips location and mobile data back **on** before locating — defeats the thief's first move. *(Most apps can't do this.)* |
| 💳 **SIM-swap → new number texted to you** | Detects a swapped SIM and auto-texts you the new SIM info + location. Follow a stolen phone to its new owner. |
| 📍 **Locate by SMS** | Text your code → get coordinates, accuracy, a reverse-geocoded address, and a Google Maps link. |
| 🔊 **Ring alarm** | Full-volume alarm even on silent — find it in the couch, or startle a thief. |
| 🔒 **Remote lock** | Lock the screen instantly via Device Admin. |
| 🪫 **Low-battery auto-locate** | Texts you its position automatically before it dies. |
| 🚫 **Anti-uninstall** | Device Admin blocks the normal uninstall path. |
| 👥 **Trusted contacts + PIN** | List trusted numbers that can command it, *or* enable a PIN so you can control it from any borrowed phone. |
| 🔐 **Secret-code auth** | Every command needs your code, matched as a whole word so a normal text can't trigger it by accident. |
| 🔁 **Reboot-persistent & resilient** | Survives restarts; retries replies with back-off until the radio has signal. |

---

## How Guardian compares

| | **Guardian** | Find My Device (Nulide) | Duper | Google FMD |
|---|:---:|:---:|:---:|:---:|
| Works with **no servers / no account** | ✅ | ✅ (server optional) | ✅ | ❌ |
| **Re-enable GPS/data a thief turned off** | ✅ (Shizuku/root) | ❌ | ❌ | ❌ |
| **SIM-swap alert with new number** | ✅ | ❌ | ❌ | ❌ |
| Locate / ring / lock by SMS | ✅ | ✅ | ✅ | partial |
| Trusted contacts **+** PIN | ✅ | ✅ | ➖ | n/a |
| Low-battery auto-locate | ✅ | ❌ | ❌ | ❌ |
| Camera photo of thief | 🔜 roadmap | ✅ | ❌ | ❌ |
| Remote wipe | 🔜 roadmap | ✅ | ❌ | ✅ |

Guardian's edge is the **"can't go dark"** column — re-enabling location/data and following the SIM. For camera and wipe we're catching up (see [roadmap](#-roadmap)); FMD is excellent and worth a look if those are your priority.

---

## 💬 Command reference

Text the phone a message containing your secret code. The code by itself = *locate*.

| Text you send | What happens |
|---|---|
| `<code>` | Reply with current location + map link |
| `<code> RING` | Sound the find-phone alarm (30s) |
| `<code> STOP` | Silence the alarm |
| `<code> LOCK` | Lock the screen now |
| `<code> DATA ON` / `<code> DATA OFF` | Toggle mobile data |
| `<code> LOC ON` | Turn high-accuracy location on |
| `<code> STATUS` | Battery %, data/GPS state, last SIM |
| `<code> HELP` | List commands |

> Example: with the code `falcon9`, texting **`falcon9 RING`** sounds the alarm and replies to you.

**Who may send commands** (dual model, inspired by Find My Device):
- Numbers in your **trusted-contacts** list can command the phone directly.
- Turn on **PIN mode** and any sender can command it *if* they include your PIN — so you can recover the phone from a friend's handset without pre-listing their number.
- Leave both empty and the secret code alone authorizes (replies to whoever sent the trigger).

---

## 🔒 Privacy: no servers, no account, no tracking

Guardian has **no backend**. There is no account to create, no server to trust, no analytics SDK, and nothing to breach remotely. Your location is computed on the device and sent as an SMS **only to the number you configured** — it never touches a third party. Secrets and settings stay in the phone's private storage.

This is the trade that makes it trustworthy: the only remote channel is a text message carrying *your* secret code.

---

## 🚀 Getting started

### Requirements
- Android 7.0 (API 24)+
- Android Studio / JDK 17 to build
- Recommended: the **[Shizuku](https://shizuku.rikka.app/)** app, so Guardian can flip data/GPS without root

### Build
```bash
git clone https://github.com/<your-username>/guardian.git
cd guardian
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
```
Or open the folder in Android Studio and hit **Run**. (`local.properties` is git-ignored and regenerated automatically.)

### Set up on the phone
1. Install the APK on the phone you want to protect.
2. Open Guardian, set your **secret code**, add your **trusted number(s)**, and optionally enable a **PIN**.
3. Grant permissions (SMS, Location, Phone state) and enable **Device admin** and **Notifications**.
4. Install **Shizuku** and tap *Request access* — or run `scripts/start_shizuku_adb.sh` over USB to start it without root.
5. From another phone, text your code to confirm you get a location back.

---

## 🧱 Architecture

```
SmsReceiver ─► CommandRouter (code) ─► Authorizer (trusted contacts / PIN) ─► LocateService
                                                                                   │
                                     ┌─────────────────────────────────────────────┤
                                     ├─► PrivilegedShell  (Shizuku / su: data + GPS ON)
                                     ├─► Locator          (fused GPS fix)
                                     ├─► AlarmPlayer      (ring)
                                     ├─► AdminReceiver    (lock / anti-uninstall)
                                     └─► Reporter         (SMS reply, retry + back-off)

BootSimReceiver   ─► reboot persistence + SIM-swap detection
LowBatteryReceiver─► auto-locate before the battery dies
```
100% Kotlin, coroutines, no backend. ~16 small, auditable files.

---

## ⚖️ Responsible use — please read

Guardian is for **protecting devices you own** and for families where everyone has agreed to it.

- ✅ **Do:** install it on your own phone, or on a child's/family member's device **with their knowledge and consent.**
- ❌ **Don't:** install it on another adult's phone to track them covertly. In most places that is illegal (stalking / wiretapping / unauthorized-access laws), and it's exactly the abuse this project refuses to enable.

You are responsible for complying with the laws where you live. Provided as-is, for lawful anti-theft and consensual family-safety use only.

---

## 🗺️ Roadmap

- [ ] 📸 Camera capture on wrong-code attempts (photo of whoever has it)
- [ ] 🧹 Remote wipe (`<code> WIPE`) with strong confirmation
- [ ] Onboarding wizard + settings polish
- [ ] Encrypted local command log
- [ ] Optional Telegram/e-mail reply channel
- [ ] Localizations & F-Droid release

Ideas and PRs welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## 📄 License

[MIT](LICENSE) © Guardian contributors.

<div align="center">

**If Guardian is useful to you, a ⭐ helps others find it.**

</div>
