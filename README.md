<div align="center">

# 🛡️ Guardian

### Open-source, serverless anti-theft & family-safety for Android — driven entirely by SMS.

**No cloud. No account. No tracking company in the middle.** Your phone talks only to *your* phone, over a text message, using a secret code only you know.

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-brightgreen)](https://developer.android.com)
[![Language](https://img.shields.io/badge/kotlin-100%25-blueviolet)](https://kotlinlang.org)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-blue.svg)](CONTRIBUTING.md)

</div>

---

## Why Guardian?

Every "find my phone" product today routes your location through someone's servers — Google, Apple, Life360, a paid subscription. That means an account, a data-broker relationship, and a thing that stops working the moment the phone has no Wi‑Fi or mobile data.

**Guardian works the opposite way.** It needs nothing but a cellular signal strong enough to send a text:

- You text your lost phone a **secret code**.
- Guardian wakes up silently, gets a GPS fix, and **texts the location (and a map link) straight back to you**.
- Nothing ever leaves the device except an SMS to the number *you* configured. No servers. No logins. No subscription. No telemetry.

It even **turns GPS and mobile data on by itself** before locating (via [Shizuku](https://shizuku.rikka.app/) — no root required, or `su` if you have it), so a thief who disabled location can't hide the phone.

---

## ✨ Features

| | |
|---|---|
| 📍 **Locate by SMS** | Text your code → get back coordinates, accuracy, a reverse-geocoded address and a Google Maps link. |
| 🔊 **Ring alarm** | Blast a loud alarm at full volume even on silent — for finding it in the couch or scaring a thief. |
| 🔒 **Remote lock** | Instantly lock the screen via Device Admin. |
| 📶 **Force data / GPS on** | Re-enables mobile data and high-accuracy location remotely (Shizuku/root) so it can't be quietly turned off. |
| 🪫 **Low-battery auto-locate** | Sends you its last position automatically before it dies. |
| 💳 **SIM-swap alert** | Detects a new SIM and texts you the new number — follow a stolen phone to its new owner. |
| 🚫 **Anti-uninstall** | Device Admin blocks a thief from removing the app the normal way. |
| 🔐 **Secret-code auth** | Every command needs your code, matched as a whole word so a normal text can never trigger it by accident. Optional sender allow-list locks it to your number only. |
| 🔁 **Reboot-persistent** | Survives restarts and keeps watching. |
| 🛰️ **Resilient replies** | Retries with back-off until the radio has signal — works even when the phone just powered on in a dead zone. |

---

## 💬 Command reference

Send a text to the phone containing your secret code. The code by itself means *locate*.

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

> Example: if your code is `falcon9`, texting **`falcon9 RING`** makes the phone sound the alarm and reply to you.

---

## 🚀 Getting started

### Requirements
- Android 7.0 (API 24) or newer
- Android Studio / JDK 17 to build
- (Optional but recommended) the **[Shizuku](https://shizuku.rikka.app/)** app, to let Guardian flip data/GPS without root

### Build

```bash
git clone https://github.com/<your-username>/guardian.git
cd guardian
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Or open the folder in Android Studio and hit **Run**.

> `local.properties` (your SDK path) is intentionally git-ignored — Android Studio recreates it on first open.

### Set up on the phone
1. Install the APK on the phone you want to protect.
2. Open the app and set your **secret code** and (optionally) your **owner number** as the allow-list.
3. Grant the permissions it asks for: SMS, Location, Phone state, and enable **Device admin** and **Notifications**.
4. Install **Shizuku** and tap *Request access* — or run `scripts/start_shizuku_adb.sh` over USB to start it without root.
5. From another phone, text the code to confirm you get a location back. Done.

---

## 🔐 How the security model works

- **Authentication is the secret code**, required on every command and matched only as a standalone token — so an ordinary message that happens to contain the word can't trigger anything.
- **Optional allow-list**: restrict all commands to a single trusted number.
- **No network surface**: Guardian has no server, no open port, no account. The only remote channel is an SMS carrying your code. There is nothing to breach remotely.
- **Privileged actions** (toggling data/GPS) go through Shizuku's shell UID or `su` — never baked-in root, and fully optional; everything else degrades gracefully without them.

See [SECURITY.md](SECURITY.md) to report a vulnerability.

---

## ⚖️ Responsible use — please read

Guardian is built for **protecting devices you own**, and for families where everyone has agreed to it. Tracking a phone's location is powerful, and the law treats it seriously.

- ✅ **Do:** install it on your own phone, or on a child's/family member's device **with their knowledge and consent.**
- ❌ **Don't:** install it on another adult's phone to monitor them covertly. In most jurisdictions that is illegal (stalking / wiretapping / unauthorized-access laws), and it is exactly the abuse this project refuses to enable.

By using Guardian you take responsibility for complying with the laws where you live. The authors provide it as-is, for lawful anti-theft and consensual family-safety use only. If you are in an unsafe situation, please reach out to a local support organization.

---

## 🧱 Architecture

```
SmsReceiver ──► CommandRouter ──► LocateService (foreground)
                   (code auth)          │
                                        ├─► PrivilegedShell  (Shizuku / su: data + GPS on)
                                        ├─► Locator          (fused GPS fix)
                                        ├─► AlarmPlayer      (ring)
                                        ├─► AdminReceiver    (lock / anti-uninstall)
                                        └─► Reporter         (SMS reply, with retry/back-off)

BootSimReceiver   ─► reboot persistence + SIM-swap detection
LowBatteryReceiver─► auto-locate before the battery dies
```

100% Kotlin, coroutines, no backend. ~15 small files — easy to read and audit.

---

## 🗺️ Roadmap

- [ ] In-app settings screen polish + onboarding wizard
- [ ] Encrypted local command log
- [ ] Optional e-mail/Telegram reply channel
- [ ] Photo capture on wrong-code attempts
- [ ] Localizations
- [ ] F-Droid release

Ideas and PRs welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## 🤝 Contributing

Issues, feature ideas and pull requests are all welcome. Good first issues are labelled as such. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## 📄 License

[MIT](LICENSE) © Guardian contributors.

<div align="center">

**If Guardian is useful to you, a ⭐ helps others find it.**

</div>
